package org.paysim.paysim.actors;

import java.util.ArrayList;
import java.util.UUID;

import sim.engine.SimState;
import sim.engine.Steppable;

import org.paysim.paysim.PaySim;
import org.paysim.paysim.parameters.Parameters;

import org.paysim.paysim.output.Output;

public class Fraudster extends SuperActor implements Steppable {
    private static final String FRAUDSTER_IDENTIFIER = "C";
    private double profit = 0;
    private int nbVictims = 0;

    public Fraudster(String name) {
        super(FRAUDSTER_IDENTIFIER + name);
    }

    @Override
    public void step(SimState state) {
        PaySim paysim = (PaySim) state;
        int step = (int) state.schedule.getSteps();
        String equip_id = UUID.randomUUID().toString();
        // 每一步以一定的概率进行欺诈,欺诈的交易次数跟balance相关
        if (paysim.random.nextDouble() < Parameters.fraudProbability) {
            Client c = paysim.pickRandomClient(getName());
            c.setFraud(true);
            double balance = c.getBalance();
            // create mule client
            if (balance > 0) {
                int nbTransactions = (int) Math.ceil(balance / Parameters.transferLimit);
                for (int i = 0; i < nbTransactions; i++) {
                    boolean transferFailed;
                    Mule muleClient = new Mule(paysim.generateId(), paysim.pickRandomBank());
                    muleClient.setFraud(true);
                    // 某个client把钱（如果有钱）转给 mule client
                    if (balance > Parameters.transferLimit) {
                        transferFailed = !c.handleTransfer(paysim, step, Parameters.transferLimit, equip_id, muleClient);
                        balance -= Parameters.transferLimit;
                    } else {
                        transferFailed = !c.handleTransfer(paysim, step, balance,equip_id, muleClient);
                        balance = 0;
                    }
                    // mule client 把钱打给空壳公司 merchant
                    profit += muleClient.getBalance();
                    muleClient.fraudulentCashOut(paysim, step, muleClient.getBalance());
                    nbVictims++;
                    paysim.addMuleClient(muleClient, this); // add the created muleClient
                    paysim.addClient(muleClient);
                    if (transferFailed)
                        break;
                }
            }
            c.setFraud(false);
        }
    }

    @Override
    public String toString() {
        ArrayList<String> properties = new ArrayList<>();

        properties.add(getName());
        properties.add(Integer.toString(nbVictims));
        properties.add(Output.fastFormatDouble(Output.PRECISION_OUTPUT, profit));

        return String.join(Output.OUTPUT_SEPARATOR, properties);
    }
}
