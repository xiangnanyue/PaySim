package org.paysim.paysim.actors;

import java.util.ArrayList;

public class Merchant extends SuperActor {
    private static final String MERCHANT_IDENTIFIER = "M";

    public Merchant(String name) {
        super(MERCHANT_IDENTIFIER + name);
        this.accounts.get(0).setPublic(true);
    }
}
