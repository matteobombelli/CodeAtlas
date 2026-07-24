package dev.codeatlas.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.codeatlas.analysis.RepositoryAnalyzer.RelationshipAnalysis;
import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RelationshipAnalyzer {

    private static final Set<String> READ_PREFIXES =
            Set.of("find", "get", "read", "exists", "count", "search");
    private static final Set<String> WRITE_PREFIXES =
            Set.of("save", "delete", "remove", "insert", "update");

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    public RelationshipAnalysis analyze(Path root, RepositoryAnalysis analysis) {
        Index index = new Index(analysis.symbols());
        List<AnalyzedRelationship> relationships = new ArrayList<>();
        List<UnresolvedRelationship> unresolved = new ArrayList<>();
        List<ExternalReference> external = new ArrayList<>();

        for (DiscoveredSourceFile file : analysis.files()) {
            try {
                Optional<CompilationUnit> unit = parser.parse(root.resolve(file.relativePath())).getResult();
                unit.ifPresent(compilation -> {
                    String packageName = compilation.getPackageDeclaration()
                            .map(value -> value.getNameAsString())
                            .orElse("");
                    for (TypeDeclaration<?> type : compilation.getTypes()) {
                        analyzeType(
                                type, packageName, "", file, index,
                                relationships, unresolved, external);
                    }
                });
            } catch (IOException ignored) {
                // JavaSourceAnalyzer owns read/parse diagnostics.
            }
        }
        return new RelationshipAnalysis(relationships, unresolved, external);
    }

    private void analyzeType(
            TypeDeclaration<?> declaration,
            String packageName,
            String enclosing,
            DiscoveredSourceFile file,
            Index index,
            List<AnalyzedRelationship> relationships,
            List<UnresolvedRelationship> unresolved,
            List<ExternalReference> external) {
        String localName = enclosing.isEmpty()
                ? declaration.getNameAsString()
                : enclosing + "." + declaration.getNameAsString();
        String qualifiedName = packageName.isEmpty() ? localName : packageName + "." + localName;
        AnalyzedSymbol type = index.typeByQualified.get(qualifiedName);
        if (type == null) {
            return;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        declaration.getFields().forEach(field -> field.getVariables().forEach(
                variable -> fields.put(variable.getNameAsString(), variable.getType().asString())));

        if (declaration instanceof ClassOrInterfaceDeclaration classType) {
            for (ClassOrInterfaceType extended : classType.getExtendedTypes()) {
                index.uniqueType(extended.getNameAsString()).ifPresent(target ->
                        relationships.add(edge(
                                type, target, RelationshipKind.EXTENDS, 0.95,
                                ResolutionMethod.AST_DECLARATION, file, extended,
                                "extends " + extended.asString())));
            }
            for (ClassOrInterfaceType implemented : classType.getImplementedTypes()) {
                index.uniqueType(implemented.getNameAsString()).ifPresent(target ->
                        relationships.add(edge(
                                type, target, RelationshipKind.IMPLEMENTS, 0.95,
                                ResolutionMethod.AST_DECLARATION, file, implemented,
                                "implements " + implemented.asString())));
            }
            inferManagedEntity(classType, type, file, index, relationships);
        }
        inferTestConvention(type, declaration, file, index, relationships);

        declaration.getConstructors().forEach(constructor ->
                constructor.getParameters().forEach(parameter ->
                        index.uniqueType(rawType(parameter.getType().asString())).ifPresent(target -> {
                            relationships.add(edge(
                                    type, target, RelationshipKind.INJECTS, 0.98,
                                    ResolutionMethod.CONSTRUCTOR_INJECTION,
                                    file, parameter,
                                    "constructor parameter " + parameter.getNameAsString()
                                            + ": " + parameter.getTypeAsString()));
                            fields.putIfAbsent(parameter.getNameAsString(), parameter.getType().asString());
                        })));

        for (MethodDeclaration method : declaration.getMethods()) {
            AnalyzedSymbol source = index.method(
                    type.id(), method.getNameAsString(), method.getParameters().size());
            if (source == null) {
                continue;
            }
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                resolveCall(
                        source, type, fields, call, file, index,
                        relationships, unresolved, external);
            }
        }

        for (TypeDeclaration<?> nested : declaration.getMembers().stream()
                .filter(TypeDeclaration.class::isInstance)
                .map(TypeDeclaration.class::cast)
                .toList()) {
            analyzeType(
                    nested, packageName, localName, file, index,
                    relationships, unresolved, external);
        }
    }

    private void inferManagedEntity(
            ClassOrInterfaceDeclaration declaration,
            AnalyzedSymbol repository,
            DiscoveredSourceFile file,
            Index index,
            List<AnalyzedRelationship> relationships) {
        boolean repositoryRole = repository.roles().contains(SymbolRole.REPOSITORY)
                || declaration.getExtendedTypes().stream().anyMatch(type ->
                        Set.of("JpaRepository", "CrudRepository", "PagingAndSortingRepository")
                                .contains(type.getNameAsString()));
        if (!repositoryRole) {
            return;
        }
        declaration.getExtendedTypes().stream()
                .filter(type -> Set.of("JpaRepository", "CrudRepository", "PagingAndSortingRepository")
                        .contains(type.getNameAsString()))
                .filter(type -> !type.getTypeArguments().orElse(new com.github.javaparser.ast.NodeList<>()).isEmpty())
                .map(type -> type.getTypeArguments().orElseThrow().get(0).asString())
                .map(RelationshipAnalyzer::rawType)
                .map(index::uniqueType)
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(entity -> relationships.add(edge(
                        repository, entity, RelationshipKind.MANAGES_ENTITY, 0.95,
                        ResolutionMethod.SPRING_DATA_GENERIC, file, declaration,
                        "Spring Data repository manages " + entity.qualifiedName())));
    }

    private void resolveCall(
            AnalyzedSymbol source,
            AnalyzedSymbol owner,
            Map<String, String> fields,
            MethodCallExpr call,
            DiscoveredSourceFile file,
            Index index,
            List<AnalyzedRelationship> relationships,
            List<UnresolvedRelationship> unresolved,
            List<ExternalReference> external) {
        Optional<AnalyzedSymbol> scopedType = call.getScope().flatMap(scope -> {
            if (scope instanceof NameExpr name) {
                String typeName = fields.getOrDefault(name.getNameAsString(), name.getNameAsString());
                return index.uniqueType(rawType(typeName));
            }
            if (scope instanceof FieldAccessExpr field
                    && field.getScope().isThisExpr()) {
                return index.uniqueType(rawType(fields.get(field.getNameAsString())));
            }
            return Optional.empty();
        });
        if (call.getScope().isEmpty()) {
            scopedType = Optional.of(owner);
        }

        List<AnalyzedSymbol> candidates = scopedType
                .map(type -> index.methods(type.id(), call.getNameAsString(), call.getArguments().size()))
                .orElseGet(List::of);
        if (candidates.isEmpty() && call.getScope().isEmpty()) {
            candidates = index.globalMethods(call.getNameAsString(), call.getArguments().size());
        }
        if (candidates.size() == 1) {
            AnalyzedSymbol target = candidates.getFirst();
            relationships.add(edge(
                    source, target,
                    source.roles().contains(SymbolRole.TEST)
                            ? RelationshipKind.TESTS : RelationshipKind.CALLS,
                    scopedType.isPresent() ? 1.0 : 0.70,
                    scopedType.isPresent()
                            ? ResolutionMethod.EXACT_PROJECT_TYPE
                            : ResolutionMethod.UNIQUE_NAME_AND_ARITY,
                    file, call, call.toString()));
            return;
        }

        if (scopedType.isPresent()
                && scopedType.get().roles().contains(SymbolRole.REPOSITORY)) {
            AnalyzedSymbol repository = scopedType.get();
            relationships.add(edge(
                    source, repository, RelationshipKind.CALLS, 0.90,
                    ResolutionMethod.SPRING_DATA_METHOD, file, call, call.toString()));
            index.managedEntity(repository.id(), relationships).ifPresent(entity -> {
                RelationshipKind kind = dataKind(call.getNameAsString());
                if (kind != null) {
                    relationships.add(edge(
                            source, entity, kind, 0.80,
                            ResolutionMethod.SPRING_DATA_METHOD,
                            file, call, call.toString()));
                }
            });
            return;
        }

        if (candidates.size() > 1) {
            unresolved.add(new UnresolvedRelationship(
                    UUID.randomUUID(), source.id(), file.id(), call.toString(),
                    RelationshipKind.CALLS, call.getBegin().orElseThrow().line,
                    "MULTIPLE_CANDIDATES", candidates.size()));
            return;
        }

        if (call.getScope().isPresent()) {
            external.add(new ExternalReference(
                    UUID.randomUUID(), source.id(), file.id(), call.toString(),
                    call.getBegin().orElseThrow().line,
                    call.getBegin().orElseThrow().column));
        } else {
            unresolved.add(new UnresolvedRelationship(
                    UUID.randomUUID(), source.id(), file.id(), call.toString(),
                    RelationshipKind.CALLS, call.getBegin().orElseThrow().line,
                    "NO_PROJECT_TARGET", 0));
        }
    }

    private void inferTestConvention(
            AnalyzedSymbol type,
            TypeDeclaration<?> declaration,
            DiscoveredSourceFile file,
            Index index,
            List<AnalyzedRelationship> relationships) {
        if (!type.roles().contains(SymbolRole.TEST)) {
            return;
        }
        String productionName = type.simpleName()
                .replaceFirst("Tests?$", "");
        index.uniqueType(productionName)
                .filter(target -> !target.id().equals(type.id()))
                .ifPresent(target -> relationships.add(edge(
                        type, target, RelationshipKind.TESTS, 0.60,
                        ResolutionMethod.TEST_NAMING_CONVENTION,
                        file, declaration,
                        "test class naming convention")));
    }

    private RelationshipKind dataKind(String methodName) {
        String lower = methodName.toLowerCase();
        if (WRITE_PREFIXES.stream().anyMatch(lower::startsWith)) {
            return RelationshipKind.WRITES_ENTITY;
        }
        if (READ_PREFIXES.stream().anyMatch(lower::startsWith)) {
            return RelationshipKind.READS_ENTITY;
        }
        return null;
    }

    private AnalyzedRelationship edge(
            AnalyzedSymbol source,
            AnalyzedSymbol target,
            RelationshipKind kind,
            double confidence,
            ResolutionMethod method,
            DiscoveredSourceFile file,
            com.github.javaparser.ast.Node evidence,
            String evidenceText) {
        var position = evidence.getBegin().orElseThrow();
        return new AnalyzedRelationship(
                UUID.randomUUID(), source.id(), target.id(), kind, confidence, method,
                file.id(), position.line, position.column, evidenceText);
    }

    private static String rawType(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<.*>", "")
                .replace("[]", "")
                .substring(value.replaceAll("<.*>", "").replace("[]", "").lastIndexOf('.') + 1);
    }

    private static final class Index {
        private final Map<String, AnalyzedSymbol> typeByQualified;
        private final Map<String, List<AnalyzedSymbol>> typesBySimple;
        private final Map<UUID, List<AnalyzedSymbol>> children;

        private Index(List<AnalyzedSymbol> symbols) {
            typeByQualified = symbols.stream()
                    .filter(Index::isType)
                    .collect(Collectors.toMap(
                            AnalyzedSymbol::qualifiedName,
                            value -> value,
                            (left, right) -> left));
            typesBySimple = symbols.stream()
                    .filter(Index::isType)
                    .collect(Collectors.groupingBy(AnalyzedSymbol::simpleName));
            children = symbols.stream()
                    .filter(symbol -> symbol.parentSymbolId() != null)
                    .collect(Collectors.groupingBy(AnalyzedSymbol::parentSymbolId));
        }

        private Optional<AnalyzedSymbol> uniqueType(String simpleName) {
            if (simpleName == null || simpleName.isBlank()) {
                return Optional.empty();
            }
            List<AnalyzedSymbol> values = typesBySimple.getOrDefault(rawType(simpleName), List.of());
            return values.size() == 1 ? Optional.of(values.getFirst()) : Optional.empty();
        }

        private AnalyzedSymbol method(UUID parent, String name, int arity) {
            List<AnalyzedSymbol> values = methods(parent, name, arity);
            return values.size() == 1 ? values.getFirst() : null;
        }

        private List<AnalyzedSymbol> methods(UUID parent, String name, int arity) {
            return children.getOrDefault(parent, List.of()).stream()
                    .filter(symbol -> symbol.kind() == SymbolKind.METHOD)
                    .filter(symbol -> symbol.simpleName().equals(name))
                    .filter(symbol -> arity(symbol.signature()) == arity)
                    .toList();
        }

        private List<AnalyzedSymbol> globalMethods(String name, int arity) {
            return children.values().stream()
                    .flatMap(List::stream)
                    .filter(symbol -> symbol.kind() == SymbolKind.METHOD)
                    .filter(symbol -> symbol.simpleName().equals(name))
                    .filter(symbol -> arity(symbol.signature()) == arity)
                    .toList();
        }

        private Optional<AnalyzedSymbol> managedEntity(
                UUID repository,
                List<AnalyzedRelationship> relationships) {
            return relationships.stream()
                    .filter(edge -> edge.sourceSymbolId().equals(repository))
                    .filter(edge -> edge.kind() == RelationshipKind.MANAGES_ENTITY)
                    .map(AnalyzedRelationship::targetSymbolId)
                    .map(target -> typeByQualified.values().stream()
                            .filter(symbol -> symbol.id().equals(target))
                            .findFirst())
                    .flatMap(Optional::stream)
                    .findFirst();
        }

        private static int arity(String signature) {
            if (signature == null || signature.equals("()")) {
                return 0;
            }
            return signature.substring(1, signature.length() - 1).split(",").length;
        }

        private static boolean isType(AnalyzedSymbol symbol) {
            return switch (symbol.kind()) {
                case CLASS, INTERFACE, ENUM, RECORD -> true;
                default -> false;
            };
        }
    }
}
