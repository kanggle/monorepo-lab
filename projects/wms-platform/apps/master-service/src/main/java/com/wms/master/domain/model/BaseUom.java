package com.wms.master.domain.model;

/**
 * Base unit of measure for an SKU. Immutable post-create.
 *
 * <ul>
 *   <li>{@code EA} — each (discrete unit, the most common WMS UoM)
 *   <li>{@code BOX} — packaged case
 *   <li>{@code PLT} — pallet
 *   <li>{@code KG} — kilogram (continuous weight)
 *   <li>{@code L} — liter (continuous volume)
 * </ul>
 */
public enum BaseUom {
    EA,
    BOX,
    PLT,
    KG,
    L
}
