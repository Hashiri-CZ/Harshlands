package me.casperge.realisticseasons;

public class RealisticSeasons {
    private static final RealisticSeasons INSTANCE = new RealisticSeasons();
    public static RealisticSeasons getInstance() { return INSTANCE; }
    public TemperatureManager getTemperatureManager() { return new TemperatureManager(); }

    public static class TemperatureManager {
        public TempData getTempData() { return new TempData(); }
        public boolean hasFahrenheitEnabled(Object player) { return false; }
        public boolean hasTemperature(Object player) { return true; }
    }

    public static class TempData {
        public boolean isEnabled() { return true; }
    }
}
