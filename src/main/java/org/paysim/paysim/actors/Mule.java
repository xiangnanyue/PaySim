package org.paysim.paysim.actors;

import org.paysim.paysim.PaySim;
import org.paysim.paysim.base.ClientProfile;
import org.paysim.paysim.base.Transaction;

import java.util.Random;
import java.util.UUID;

public class Mule extends Client {
    private static final String MULE_IDENTIFIER = "C";
    private String ip;
    private String equip_id;

    // construct a fraudster Mule account, it has its own rule of generate
    // city, country ips, ip, equip_id, ect.
    public Mule(String name, Bank bank) {
        super(MULE_IDENTIFIER + name, bank);
        this.overdraftLimit = 0;
        this.city = new String[]{"Shanghai", "Washington", "Paris"}[new Random().nextInt(3)];
        this.country = new String[]{"China", "America", "France"}[new Random().nextInt(3)];
        this.ips = ClientProfile.generateIPs(100);
        this.ip = this.ips[new Random().nextInt(100)];
        this.equip_id = UUID.randomUUID().toString();
    }

    void fraudulentCashOut(PaySim paysim, int step, double amount) {
        String action = "CASH_OUT";

        Merchant merchantTo = paysim.pickRandomMerchant();
        String nameOrig = this.getName();
        String nameDest = merchantTo.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = merchantTo.getBalance();

        this.withdraw(amount);

        double newBalanceOrig = this.getBalance();
        double newBalanceDest = merchantTo.getBalance();
        Account orgAccount = this.accounts.get(0);
        Account desAccount = merchantTo.accounts.get(0);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        Transaction t = new Transaction(step, action, amount, nameOrig, oldBalanceOrig,
                newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, city,
                country, ip, equip_id, isInnerTransaction,
                orgAccount.isPublic, desAccount.isPublic,
                orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

        t.setFraud(this.isFraud());
        paysim.getTransactions().add(t);
    }
}
