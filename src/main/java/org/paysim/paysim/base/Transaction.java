package org.paysim.paysim.base;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;
import java.util.Date;

import org.paysim.paysim.output.Output;

public class Transaction implements Serializable {
    private static final String[] transactionChannels = {"POS机", "网上银行", "快捷支付"};
    private static final Date startDate = new Date(1500000000000L);  //Fri Jul 14 10:40:00 CST 2017

    private static long serialVersionUID = 1L;
    private long id=0;

    private final int step;
    private final String action;
    private final double amount;

    private final String nameOrig;
    private final double oldBalanceOrig, newBalanceOrig;

    private final String nameDest;
    private final double oldBalanceDest, newBalanceDest;

    private final String city, country, ip, equip_id, transaction_channel;
    private final String orgAccountNum, desAccountNum, orgCardType;
    private final boolean isFinished, isInnerTransaction, isOrgPublic, isDesPublic;
    private Date pay_time;

    private boolean isFraud = false;
    private boolean isFlaggedFraud = false;
    private boolean isUnauthorizedOverdraft = false;

    public Transaction(int step, String action, double amount, String nameOrig,
                       double oldBalanceOrig, double newBalanceOrig, String nameDest,
                       double oldBalanceDest, double newBalanceDest, String city,
                       String country, String ip, String equip_id,
                       boolean isInnerTransaction, boolean isOrgPublic,
                       boolean isDesPublic, String orgAccountNum,
                       String desAccountNum, String orgCardType) {
        this.step = step;
        this.action = action;
        this.amount = amount;
        this.nameOrig = nameOrig;
        this.oldBalanceOrig = oldBalanceOrig;
        this.newBalanceOrig = newBalanceOrig;
        this.nameDest = nameDest;
        this.oldBalanceDest = oldBalanceDest;
        this.newBalanceDest = newBalanceDest;
        this.city = city;
        this.country = country;
        this.ip = ip;
        this.equip_id = equip_id;
        // TODO: add to agent logic in the future
        this.transaction_channel = transactionChannels[new Random().nextInt(transactionChannels.length)];
        this.isFinished = new Random().nextBoolean();
        this.pay_time = new Date(startDate.getTime()+step*3600+id*10);
        this.isInnerTransaction = isInnerTransaction;
        this.isOrgPublic = isOrgPublic;
        this.isDesPublic = isDesPublic;
        this.orgAccountNum = orgAccountNum;
        this.desAccountNum = desAccountNum;
        this.orgCardType = orgCardType;

        id = serialVersionUID++;
    }

    public boolean isFailedTransaction(){
        return isFlaggedFraud || isUnauthorizedOverdraft;
    }

    public void setFlaggedFraud(boolean isFlaggedFraud) {
        this.isFlaggedFraud = isFlaggedFraud;
    }

    public void setFraud(boolean isFraud) {
        this.isFraud = isFraud;
    }

    public void setUnauthorizedOverdraft(boolean isUnauthorizedOverdraft) {
        this.isUnauthorizedOverdraft = isUnauthorizedOverdraft;
    }

    public boolean isFlaggedFraud() {
        return isFlaggedFraud;
    }

    public boolean isFraud() {
        return isFraud;
    }

    public int getStep() {
        return step;
    }

    public String getAction() {
        return action;
    }

    public double getAmount() {
        return amount;
    }

    public String getNameOrig() {
        return nameOrig;
    }

    public double getOldBalanceOrig() {
        return oldBalanceOrig;
    }

    public double getNewBalanceOrig() {
        return newBalanceOrig;
    }

    public String getNameDest() {
        return nameDest;
    }

    public double getOldBalanceDest() {
        return oldBalanceDest;
    }

    public double getNewBalanceDest() {
        return newBalanceDest;
    }

    @Override
    public String toString(){
        ArrayList<String> properties = new ArrayList<>();

        properties.add(String.valueOf(step));
        properties.add(action);
        properties.add(Output.fastFormatDouble(Output.PRECISION_OUTPUT, amount));
        properties.add(nameOrig);
        properties.add(Output.fastFormatDouble(Output.PRECISION_OUTPUT, oldBalanceOrig));
        properties.add(Output.fastFormatDouble(Output.PRECISION_OUTPUT, newBalanceOrig));
        properties.add(nameDest);
        properties.add(Output.fastFormatDouble(Output.PRECISION_OUTPUT, oldBalanceDest));
        properties.add(Output.fastFormatDouble(Output.PRECISION_OUTPUT, newBalanceDest));
        properties.add(Output.formatBoolean(isFraud));
        properties.add(Output.formatBoolean(isFlaggedFraud));
        properties.add(Output.formatBoolean(isUnauthorizedOverdraft));
        properties.add(city);
        properties.add(country);
        properties.add(ip);
        properties.add(equip_id);
        // TODO: use standard format
        properties.add(transaction_channel);
        properties.add(Output.formatBoolean(isFinished));
        properties.add(pay_time.toString());

        properties.add(orgAccountNum);
        properties.add(desAccountNum);
        properties.add(Output.formatBoolean(isInnerTransaction));
        properties.add(Output.formatBoolean(isOrgPublic));
        properties.add(Output.formatBoolean(isDesPublic));
        properties.add(orgCardType);
        properties.add(String.valueOf(id));

        return String.join(Output.OUTPUT_SEPARATOR, properties);
    }
}
