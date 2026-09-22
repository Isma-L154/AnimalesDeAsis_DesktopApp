package com.asosiaciondeasis.animalesdeasis.Abstraccions;

/**
 * What a local row looked like when synchronisation read it, so a later write can
 * tell whether the row was edited in between.
 *
 * <p>{@code lastModified} alone is not enough: it has one-second precision, so an
 * edit saved in the same second as the read leaves it unchanged. Every local
 * edit also sets {@code synced} to false, which is what catches that case.</p>
 */
public record RowVersion(String lastModified, boolean synced) {
}
