package com.campusconnect.campus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
class CampusMap {
    private final double south,north,west,east;
    private final String browserKey,mapId;
    CampusMap(@Value("${CAMPUS_SOUTH:10.292}") double south,@Value("${CAMPUS_NORTH:10.299}") double north,
              @Value("${CAMPUS_WEST:123.877}") double west,@Value("${CAMPUS_EAST:123.885}") double east,
              @Value("${GOOGLE_MAPS_API_KEY:}") String browserKey,@Value("${GOOGLE_MAP_ID:DEMO_MAP_ID}") String mapId) {
        if (!(south>=-90 && north<=90 && south<north && west>=-180 && east<=180 && west<east))
            throw new IllegalArgumentException("Invalid campus bounds.");
        this.south=south; this.north=north; this.west=west; this.east=east;
        this.browserKey=browserKey; this.mapId=mapId;
    }
    @GetMapping("/api/campus/map")
    public Map<String,Object> configuration() {
        return Map.of("bounds",Map.of("south",south,"north",north,"west",west,"east",east),
            "center",Map.of("lat",(south+north)/2,"lng",(east+west)/2),"key",browserKey,"mapId",mapId);
    }
    void validate(Double lat,Double lng) {
        if (lat==null && lng==null) return;
        if (lat==null || lng==null || !(lat>=south && lat<=north && lng>=west && lng<=east))
            throw CampusData.invalid("Choose a point inside the configured campus area.");
    }
}
