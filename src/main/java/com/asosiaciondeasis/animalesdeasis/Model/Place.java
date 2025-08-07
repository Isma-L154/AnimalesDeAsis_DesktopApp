package com.asosiaciondeasis.animalesdeasis.Model;

public class Place {

    private int id;
    private String name;
    private String provinceId;
    private String provinceName;

    public Place(int id, String name, String provinceId, String provinceName) {
        this.id = id;
        this.name = name;
        this.provinceId = provinceId;
        this.provinceName = provinceName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvinceId() {
        return provinceId;
    }

    public void setProvinceId(String provinceId) {
        this.provinceId = provinceId;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public void setProvinceName(String provinceName) {
        this.provinceName = provinceName;
    }

    @Override
    public String toString() {
        return name; // Display name in ComboBox
    }
}
