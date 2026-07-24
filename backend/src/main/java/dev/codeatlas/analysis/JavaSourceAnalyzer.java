package dev.codeatlas.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JavaSourceAnalyzer {

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    public RepositoryAnalysis analyze(Path root, List<DiscoveredSourceFile> files) {
        List<AnalyzedSymbol> symbols = new ArrayList<>();
        List<AnalyzedEndpoint> endpoints = new ArrayList<>();
        List<AnalysisWarning> warnings = new ArrayList<>();
        Map<UUID, String> packages = new LinkedHashMap<>();

        for (DiscoveredSourceFile file : files) {
            Path source = root.resolve(file.relativePath());
            try {
                ParseResult<CompilationUnit> result = parser.parse(source);
                if (result.getResult().isEmpty()) {
                    String message = result.getProblems().isEmpty()
                            ? "JavaParser did not produce a compilation unit"
                            : result.getProblems().getFirst().getMessage();
                    warnings.add(new AnalysisWarning(
                            UUID.randomUUID(), file.id(), "PARSE_ERROR", message, null));
                    continue;
                }
                CompilationUnit unit = result.getResult().get();
                String packageName = unit.getPackageDeclaration()
                        .map(declaration -> declaration.getNameAsString())
                        .orElse("");
                packages.put(file.id(), packageName);
                for (TypeDeclaration<?> type : unit.getTypes()) {
                    analyzeType(
                            file, type, null, packageName, "", symbols, endpoints, warnings);
                }
                result.getProblems().forEach(problem -> warnings.add(new AnalysisWarning(
                        UUID.randomUUID(),
                        file.id(),
                        "PARSE_WARNING",
                        problem.getMessage(),
                        problem.getLocation()
                                .flatMap(location -> location.getBegin().getRange())
                                .map(range -> range.begin.line)
                                .orElse(null))));
            } catch (IOException exception) {
                warnings.add(new AnalysisWarning(
                        UUID.randomUUID(),
                        file.id(),
                        "READ_ERROR",
                        exception.getMessage(),
                        null));
            }
        }
        return new RepositoryAnalysis(files, packages, symbols, endpoints, warnings);
    }

    private void analyzeType(
            DiscoveredSourceFile file,
            TypeDeclaration<?> type,
            UUID parentId,
            String packageName,
            String enclosingName,
            List<AnalyzedSymbol> symbols,
            List<AnalyzedEndpoint> endpoints,
            List<AnalysisWarning> warnings) {
        String typeName = enclosingName.isEmpty()
                ? type.getNameAsString()
                : enclosingName + "." + type.getNameAsString();
        String qualifiedName = packageName.isEmpty() ? typeName : packageName + "." + typeName;
        UUID typeId = UUID.randomUUID();
        Set<SymbolRole> roles = roles(type);
        symbols.add(symbol(
                file, typeId, parentId, kind(type), type.getNameAsString(), qualifiedName,
                null, type, type.hasModifier(Modifier.Keyword.ABSTRACT), type.isStatic(), roles));

        List<String> controllerPaths = roles.contains(SymbolRole.CONTROLLER)
                ? mappingPaths(type.getAnnotations(), "RequestMapping")
                : List.of();
        if (controllerPaths.isEmpty()) {
            controllerPaths = List.of("");
        }

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof MethodDeclaration method) {
                UUID methodId = UUID.randomUUID();
                String signature = signature(method);
                Set<SymbolRole> methodRoles = roles(method);
                symbols.add(symbol(
                        file, methodId, typeId, SymbolKind.METHOD, method.getNameAsString(),
                        qualifiedName + "." + method.getNameAsString(), signature, method,
                        method.isAbstract(), method.isStatic(), methodRoles));
                collectEndpoints(method, methodId, controllerPaths, endpoints);
            } else if (member instanceof ConstructorDeclaration constructor) {
                symbols.add(symbol(
                        file, UUID.randomUUID(), typeId, SymbolKind.CONSTRUCTOR,
                        constructor.getNameAsString(), qualifiedName + ".<init>",
                        signature(constructor), constructor, false, false, Set.of()));
            } else if (member instanceof FieldDeclaration field) {
                for (var variable : field.getVariables()) {
                    symbols.add(symbol(
                            file, UUID.randomUUID(), typeId, SymbolKind.FIELD,
                            variable.getNameAsString(),
                            qualifiedName + "." + variable.getNameAsString(),
                            variable.getType().asString(), variable,
                            false, field.isStatic(), Set.of()));
                }
            } else if (member instanceof TypeDeclaration<?> nested) {
                analyzeType(
                        file, nested, typeId, packageName, typeName,
                        symbols, endpoints, warnings);
            }
        }
    }

    private void collectEndpoints(
            MethodDeclaration method,
            UUID methodId,
            List<String> controllerPaths,
            List<AnalyzedEndpoint> endpoints) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String annotationName = annotation.getName().getIdentifier();
            String httpMethod = switch (annotationName) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "PatchMapping" -> "PATCH";
                case "DeleteMapping" -> "DELETE";
                case "RequestMapping" -> requestMappingMethod(annotation);
                default -> null;
            };
            if (httpMethod == null) {
                continue;
            }
            List<String> methodPaths = mappingPaths(List.of(annotation), annotationName);
            if (methodPaths.isEmpty()) {
                methodPaths = List.of("");
            }
            for (String controllerPath : controllerPaths) {
                for (String methodPath : methodPaths) {
                    endpoints.add(new AnalyzedEndpoint(
                            UUID.randomUUID(),
                            methodId,
                            httpMethod,
                            combinePath(controllerPath, methodPath),
                            method.getParameters().stream()
                                    .filter(parameter -> parameter.getAnnotations().stream()
                                            .anyMatch(value -> value.getName()
                                                    .getIdentifier()
                                                    .equals("RequestBody")))
                                    .map(parameter -> parameter.getType().asString())
                                    .findFirst()
                                    .orElse(null),
                            method.getType().asString()));
                }
            }
        }
    }

    private AnalyzedSymbol symbol(
            DiscoveredSourceFile file,
            UUID id,
            UUID parentId,
            SymbolKind kind,
            String simpleName,
            String qualifiedName,
            String signature,
            Node node,
            boolean abstractSymbol,
            boolean staticSymbol,
            Set<SymbolRole> roles) {
        var range = node.getRange().orElseThrow();
        String keyMaterial = file.relativePath() + "|" + kind + "|" + qualifiedName
                + "|" + (signature == null ? "" : signature);
        return new AnalyzedSymbol(
                id,
                file.id(),
                parentId,
                sha256(keyMaterial),
                kind,
                simpleName,
                qualifiedName,
                signature,
                visibility(node),
                range.begin.line,
                range.end.line,
                range.begin.column,
                range.end.column,
                abstractSymbol,
                staticSymbol,
                roles);
    }

    private SymbolKind kind(TypeDeclaration<?> type) {
        if (type.isRecordDeclaration()) {
            return SymbolKind.RECORD;
        }
        if (type.isEnumDeclaration()) {
            return SymbolKind.ENUM;
        }
        if (type instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface()) {
            return SymbolKind.INTERFACE;
        }
        return SymbolKind.CLASS;
    }

    private Set<SymbolRole> roles(Node node) {
        EnumSet<SymbolRole> result = EnumSet.noneOf(SymbolRole.class);
        if (!(node instanceof com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> annotated)) {
            return result;
        }
        for (AnnotationExpr annotation : annotated.getAnnotations()) {
            switch (annotation.getName().getIdentifier()) {
                case "RestController", "Controller" -> result.add(SymbolRole.CONTROLLER);
                case "Service" -> result.add(SymbolRole.SERVICE);
                case "Repository" -> result.add(SymbolRole.REPOSITORY);
                case "Component" -> result.add(SymbolRole.COMPONENT);
                case "Configuration" -> result.add(SymbolRole.CONFIGURATION);
                case "Entity" -> result.add(SymbolRole.ENTITY);
                case "Test", "ParameterizedTest", "SpringBootTest", "WebMvcTest", "DataJpaTest" ->
                        result.add(SymbolRole.TEST);
                default -> {
                }
            }
        }
        return result;
    }

    private String signature(CallableDeclaration<?> callable) {
        return "(" + callable.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .reduce((left, right) -> left + "," + right)
                .orElse("") + ")";
    }

    private String visibility(Node node) {
        if (node instanceof com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> modifiers) {
            if (modifiers.hasModifier(Modifier.Keyword.PUBLIC)) {
                return "PUBLIC";
            }
            if (modifiers.hasModifier(Modifier.Keyword.PROTECTED)) {
                return "PROTECTED";
            }
            if (modifiers.hasModifier(Modifier.Keyword.PRIVATE)) {
                return "PRIVATE";
            }
        }
        return "PACKAGE";
    }

    private List<String> mappingPaths(
            Iterable<AnnotationExpr> annotations,
            String expectedName) {
        for (AnnotationExpr annotation : annotations) {
            if (!annotation.getName().getIdentifier().equals(expectedName)) {
                continue;
            }
            if (annotation.isMarkerAnnotationExpr()) {
                return List.of("");
            }
            if (annotation.isSingleMemberAnnotationExpr()) {
                return strings(annotation.asSingleMemberAnnotationExpr().getMemberValue());
            }
            Optional<MemberValuePair> pair = annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(value -> value.getNameAsString().equals("value")
                            || value.getNameAsString().equals("path"))
                    .findFirst();
            return pair.map(value -> strings(value.getValue())).orElse(List.of(""));
        }
        return List.of();
    }

    private List<String> strings(Expression expression) {
        if (expression instanceof StringLiteralExpr literal) {
            return List.of(literal.asString());
        }
        if (expression instanceof ArrayInitializerExpr array) {
            return array.getValues().stream().flatMap(value -> strings(value).stream()).toList();
        }
        if (expression instanceof BinaryExpr binary
                && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            List<String> left = strings(binary.getLeft());
            List<String> right = strings(binary.getRight());
            if (left.size() == 1 && right.size() == 1) {
                return List.of(left.getFirst() + right.getFirst());
            }
        }
        return List.of();
    }

    private String requestMappingMethod(AnnotationExpr annotation) {
        if (!annotation.isNormalAnnotationExpr()) {
            return "ANY";
        }
        return annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("method"))
                .map(pair -> pair.getValue().toString())
                .map(value -> value.replace("RequestMethod.", "").replaceAll("[{}\\[\\] ]", ""))
                .filter(value -> !value.contains(","))
                .findFirst()
                .orElse("ANY");
    }

    private String combinePath(String controller, String method) {
        String joined = ("/" + controller + "/" + method).replaceAll("/+", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            return joined.substring(0, joined.length() - 1);
        }
        return joined;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
