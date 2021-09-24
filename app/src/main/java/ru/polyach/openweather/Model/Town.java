package ru.polyach.openweather.Model;

import ru.polyach.openweather.Model.Coord;

public class Town {
    private String id;
    private String name;
    private String country;
    private String state;
    private Coord coord;

    public Town() {
    }

    public Town(String id, String name, String country, String state, Coord coord) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.state = state;
        this.coord = coord;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Coord getCoord() {
        return coord;
    }

    public void setCoord(Coord coord) {
        this.coord = coord;
    }
}

