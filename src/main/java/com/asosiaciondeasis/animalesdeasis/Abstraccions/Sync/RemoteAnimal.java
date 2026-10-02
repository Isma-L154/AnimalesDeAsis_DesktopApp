package com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.List;

/**
 * An animal as the shared copy holds it, with every vaccine it has there.
 *
 * @param vaccines all of the animal's remote vaccines, not only the changed
 *                 ones: a vaccine missing from this list was deleted
 */
public record RemoteAnimal(Animal animal, List<Vaccine> vaccines) {
}
