package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

public class VaccineManagementController implements IPortalAwareController {

    private PortalController portalController;
    private Animal currentAnimal;


    public void setCurrentAnimal(Animal animal) {
        this.currentAnimal = animal;
    }

    @Override
    public void setPortalController(PortalController controller) {this.portalController = controller;}
}
