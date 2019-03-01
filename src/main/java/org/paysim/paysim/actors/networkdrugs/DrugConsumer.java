package org.paysim.paysim.actors.networkdrugs;

import ec.util.MersenneTwisterFast;
import org.paysim.paysim.parameters.Parameters;
import sim.engine.SimState;

import org.paysim.paysim.PaySim;
import org.paysim.paysim.actors.Client;
import org.paysim.paysim.utils.RandomCollection;

import java.util.UUID;

public class DrugConsumer extends Client {
    private DrugDealer dealer;
    private RandomCollection<Double> probAmountProfile;
    private double probabilityBuy;

    public DrugConsumer(PaySim paySim, DrugDealer dealer, double monthlySpending, RandomCollection<Double> probAmountProfile, double meanTr) {
        super(paySim);
        this.dealer = dealer;
        this.probAmountProfile = probAmountProfile;
        this.probabilityBuy = monthlySpending / meanTr / Parameters.nbSteps;
    }

    @Override
    public void step(SimState state) {
        PaySim paySim = (PaySim) state;
        int step = (int) paySim.schedule.getSteps();
        String equip_id = UUID.randomUUID().toString();
        super.step(state);

        if (wantsToBuyDrugs(paySim.random)) {
            double amount = pickAmount();

            handleTransferDealer(paySim, step, amount, equip_id);
        }
    }

    private void handleTransferDealer(PaySim paySim, int step, double amount, String equip_id) {
        boolean success = handleTransfer(paySim, step, amount, equip_id, dealer);

        if (success) {
            dealer.addMoneyFromDrug(amount);
        }
    }

    private boolean wantsToBuyDrugs(MersenneTwisterFast random) {
        return random.nextBoolean(probabilityBuy);
    }

    private double pickAmount() {
        return probAmountProfile.next();
    }
}
