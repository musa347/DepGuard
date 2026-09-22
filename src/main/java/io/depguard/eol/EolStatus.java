package io.depguard.eol;

/** EOL lifecycle status of a product/cycle as reported by endoflife.date. */
public enum EolStatus {
    SUPPORTED,
    MAINTENANCE,
    EOL,
    UNKNOWN
}
