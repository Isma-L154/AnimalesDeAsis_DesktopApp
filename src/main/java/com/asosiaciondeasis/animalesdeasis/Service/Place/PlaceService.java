package com.asosiaciondeasis.animalesdeasis.Service.Place;


import com.asosiaciondeasis.animalesdeasis.Abstraccions.Places.IPlaceDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Places.IPlacesService;
import com.asosiaciondeasis.animalesdeasis.Model.Place;

import java.util.List;

public class PlaceService implements IPlacesService {

    private final IPlaceDAO placeDAO;

    public PlaceService(IPlaceDAO placeDAO) {
        this.placeDAO = placeDAO;
    }

    @Override
    public List<Place> getAllPlaces() {
        return placeDAO.getAllPlaces();
    }
}
