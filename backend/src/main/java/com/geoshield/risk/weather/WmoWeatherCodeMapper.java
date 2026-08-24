package com.geoshield.risk.weather;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a WMO present-weather code, as reported by the weather provider, onto one of the five
 * weather conditions MoRTH publishes in Table 3.8.
 *
 * <p>The mapping is deliberately small, total on the codes a provider actually emits, and
 * explicitly partial everywhere else. Every assignment below is justified by the physical
 * phenomenon the WMO code describes:
 *
 * <table border="1">
 *   <caption>Documented WMO to MoRTH category mapping</caption>
 *   <tr><th>WMO codes</th><th>WMO meaning</th><th>MoRTH category</th><th>Why</th></tr>
 *   <tr><td>0, 1, 2, 3</td><td>Clear, mainly clear, partly cloudy, overcast</td>
 *       <td>Sunny / clear</td>
 *       <td>No precipitation and no obscuration. MoRTH's "Sunny / clear" is the
 *           no-adverse-phenomenon condition, so plain cloud cover belongs here.</td></tr>
 *   <tr><td>45, 48</td><td>Fog, depositing rime fog</td><td>Foggy &amp; misty</td>
 *       <td>Direct match to the published condition.</td></tr>
 *   <tr><td>51, 53, 55, 61, 63, 65, 80, 81, 82</td>
 *       <td>Drizzle, rain, rain showers (all intensities)</td><td>Rainy</td>
 *       <td>Liquid precipitation. Direct match.</td></tr>
 *   <tr><td>95</td><td>Thunderstorm, slight or moderate</td><td>Rainy</td>
 *       <td>Rain-bearing convective weather whose precipitation is liquid. Mapped to the
 *           observable liquid-precipitation condition rather than to "Others", because
 *           "Others" is an unknown-composition bucket and also the most severe published
 *           row - assigning a thunderstorm there would assert the worst severity on a
 *           guess about what MoRTH counted.</td></tr>
 *   <tr><td>56, 57, 66, 67</td><td>Freezing drizzle, freezing rain</td><td>Hail / sleet</td>
 *       <td>Freezing precipitation is sleet.</td></tr>
 *   <tr><td>71, 73, 75, 77, 85, 86</td><td>Snowfall, snow grains, snow showers</td>
 *       <td>Hail / sleet</td>
 *       <td>MoRTH publishes no snow row. "Hail / sleet" is its only frozen-precipitation
 *           condition, so snow is mapped there rather than inventing a sixth category with
 *           no published accident statistics behind it. Recorded as a limitation.</td></tr>
 *   <tr><td>96, 99</td><td>Thunderstorm with slight or heavy hail</td><td>Hail / sleet</td>
 *       <td>Explicit hail. Direct match.</td></tr>
 * </table>
 *
 * <p>Any other code - including codes a provider may add in future - is intentionally
 * <strong>unmapped</strong>, so the weather factor reports unavailable instead of being forced
 * into an approximate category. MoRTH's "Others" row is never a mapping target, for the reason
 * given above and in {@link WeatherCategory#OTHERS}.
 */
public final class WmoWeatherCodeMapper {
    private static final Map<Integer, WeatherCategory> MAPPING = buildMapping();

    private WmoWeatherCodeMapper() {
    }

    /**
     * The MoRTH category for a WMO code, or empty when the code is outside the documented mapping.
     *
     * <p>Empty is a real answer, not an error: the caller must surface it as an explicitly
     * unavailable weather factor.
     */
    public static Optional<WeatherCategory> categoryOf(int wmoCode) {
        return Optional.ofNullable(MAPPING.get(wmoCode));
    }

    /** The full documented mapping, exposed so tests and documentation stay in step with it. */
    public static Map<Integer, WeatherCategory> mapping() {
        return MAPPING;
    }

    private static Map<Integer, WeatherCategory> buildMapping() {
        Map<Integer, WeatherCategory> mapping = new LinkedHashMap<>();
        put(mapping, WeatherCategory.SUNNY_CLEAR, 0, 1, 2, 3);
        put(mapping, WeatherCategory.FOGGY_MISTY, 45, 48);
        put(mapping, WeatherCategory.RAINY, 51, 53, 55, 61, 63, 65, 80, 81, 82, 95);
        put(mapping, WeatherCategory.HAIL_SLEET, 56, 57, 66, 67, 71, 73, 75, 77, 85, 86, 96, 99);
        return Map.copyOf(mapping);
    }

    private static void put(Map<Integer, WeatherCategory> mapping, WeatherCategory category, int... wmoCodes) {
        for (int wmoCode : wmoCodes) {
            WeatherCategory previous = mapping.put(wmoCode, category);
            if (previous != null) {
                throw new IllegalStateException("WMO code " + wmoCode + " is mapped twice");
            }
        }
    }
}
