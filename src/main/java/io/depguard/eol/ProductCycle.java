package io.depguard.eol;

/** A product and its version cycle as understood by endoflife.date (e.g. {@code spring-boot / 2.7}). */
public record ProductCycle(String product, String cycle) {}
