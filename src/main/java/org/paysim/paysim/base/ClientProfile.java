package org.paysim.paysim.base;

import java.util.*;
import java.util.stream.Collectors;

import ec.util.MersenneTwisterFast;

import org.paysim.paysim.parameters.ActionTypes;

public class ClientProfile {
    public static final Map<String, String> city2Country;
    static {
        String[] cityCountryList = TimeZone.getAvailableIDs();
        Map<String, String> tmp = new HashMap<>();
        int i = 0;
        for (String s: cityCountryList) {
            String[] countryCity = s.split("/");
            if (countryCity.length == 2) tmp.put(countryCity[1], countryCity[0]);
            else continue;
        }
        city2Country = Collections.unmodifiableMap(tmp);
    }
    public static String[] cities = city2Country.keySet().toArray(new String[city2Country.size()]);

    private Map<String, ClientActionProfile> actionProfileMap;
    private Map<String, Double> actionProbability = new HashMap<>();
    private final Map<String, Integer> targetCount = new HashMap<>();
    private int clientTargetCount;

    public ClientProfile(Map<String, ClientActionProfile> actionProfile, MersenneTwisterFast random) {
        this.actionProfileMap = actionProfile;
        this.clientTargetCount = 0;
        for (String action : ActionTypes.getActions()) {
            int targetCountAction = pickTargetCount(action, random);
            targetCount.put(action, targetCountAction);
            clientTargetCount += targetCountAction;
        }
        computeActionProbability();
    }

    public static String[] generateIPs(int n) {
        String[] ips = new String[n];
        for(int i=0; i < n; i++){
            String[] values = new String[4];
            for(int j = 0; j<4; j++){
                if (j == 0) values[j] = Integer.toString(new Random().nextInt(233)+1);
                else values[j] = Integer.toString(new Random().nextInt(255));
            }
            String newIP = String.join(".", values);
            ips[i] = newIP;
        }
        return ips;
    }

    private int pickTargetCount(String action, MersenneTwisterFast random) {
        ClientActionProfile actionProfile = actionProfileMap.get(action);
        int targetCountAction;

        int rangeSize = actionProfile.getMaxCount() - actionProfile.getMinCount();

        if (rangeSize == 0) {
            targetCountAction = actionProfile.getMinCount();
        } else {
            targetCountAction = actionProfile.getMinCount() + random.nextInt(rangeSize);
        }

        //TODO: check if this is really mandatory
        int maxCountAction = ActionTypes.getMaxOccurrenceGivenAction(actionProfile.getAction());
        if (targetCountAction > maxCountAction) {
            targetCountAction = maxCountAction;
        }

        return targetCountAction;
    }

    private void computeActionProbability() {
        actionProbability = targetCount.entrySet()
                .stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        c -> ((double) c.getValue()) / clientTargetCount)
                );
    }

    public Map<String, Double> getActionProbability() {
        return actionProbability;
    }

    public int getClientTargetCount() {
        return clientTargetCount;
    }

    public ClientActionProfile getProfilePerAction(String action) {
        return actionProfileMap.get(action);
    }
}
