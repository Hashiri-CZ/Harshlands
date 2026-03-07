package me.casperge.realisticseasons.api;

public class SeasonsAPI {
    private static final SeasonsAPI INSTANCE = new SeasonsAPI();
    public static SeasonsAPI getInstance() { return INSTANCE; }
    public int getTemperature(Object player) { return 0; }
}
