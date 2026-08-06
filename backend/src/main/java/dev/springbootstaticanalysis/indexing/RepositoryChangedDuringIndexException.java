package dev.springbootstaticanalysis.indexing;

public class RepositoryChangedDuringIndexException extends RuntimeException {

    public RepositoryChangedDuringIndexException() {
        super("Repository source files changed while indexing was in progress");
    }
}
