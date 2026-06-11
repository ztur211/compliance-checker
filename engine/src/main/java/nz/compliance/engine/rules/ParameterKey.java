package nz.compliance.engine.rules;

/** A checkable quantity the engine can extract. Add an extractor in ParameterRegistry when adding one. */
public enum ParameterKey {
    OPEN_PATH_LENGTH(Scope.PER_SPACE),
    OCCUPANT_LOAD(Scope.PER_SPACE),
    EXIT_COUNT(Scope.WHOLE_PLAN),
    EXIT_WIDTH(Scope.WHOLE_PLAN);

    private final Scope scope;

    ParameterKey(Scope scope) { this.scope = scope; }

    public Scope scope() { return scope; }
}
