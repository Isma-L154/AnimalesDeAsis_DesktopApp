package com.asosiaciondeasis.animalesdeasis.Model;

/** A canton an animal can be rescued from, with the province it belongs to. */
public record Place(int id, String name, String provinceName) {

    /** What a ComboBox shows. */
    @Override
    public String toString() {
        return name;
    }
}
