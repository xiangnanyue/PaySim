package org.paysim.paysim.actors;

import java.util.*;

import static java.lang.Math.max;

import ec.util.MersenneTwisterFast;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.distribution.Binomial;

import org.paysim.paysim.PaySim;

import org.paysim.paysim.base.ClientActionProfile;
import org.paysim.paysim.base.ClientProfile;
import org.paysim.paysim.base.StepActionProfile;
import org.paysim.paysim.base.Transaction;

import org.paysim.paysim.parameters.ActionTypes;
import org.paysim.paysim.parameters.Parameters;
import org.paysim.paysim.parameters.BalancesClients;

import org.paysim.paysim.utils.RandomCollection;


public class Client extends SuperActor implements Steppable {
    private static final String CLIENT_IDENTIFIER = "C";
    private static final int MIN_NB_TRANSFER_FOR_FRAUD = 3;
    private static final String CASH_IN = "CASH_IN", CASH_OUT = "CASH_OUT", DEBIT = "DEBIT",
            PAYMENT = "PAYMENT", TRANSFER = "TRANSFER", DEPOSIT = "DEPOSIT";
    private static final String[] CARD_TYPES = {"信用卡", "储蓄卡"};
    private static final int MAX_CARD_NUM = 7;  //a client has at most 7+1 cards. first card is initialzed by default

    private final Bank bank;
    private ClientProfile clientProfile;
    private double clientWeight;
    private double balanceMax = 0;
    private int countTransferTransactions = 0;
    private double expectedAvgTransaction = 0;
    private double initialBalance;

    protected String country;
    protected String city;
    protected String[] ips;
    protected String[] equip_ids;

    Client(String name, Bank bank) {
        super(name); //CLIENT_IDENTIFIER +
        this.bank = bank;
        // accounts
        this.accounts.get(0).setBank(this.bank);
        this.accounts.get(0).setCardType(CARD_TYPES[new Random().nextInt(CARD_TYPES.length)]);
    }

    // this is constructor used for normal clients (not fraudsters)
    public Client(PaySim paySim) {
        super(CLIENT_IDENTIFIER + paySim.generateId());
        this.bank = paySim.pickRandomBank();
        this.clientProfile = new ClientProfile(paySim.pickNextClientProfile(), paySim.random);
        this.clientWeight = ((double) clientProfile.getClientTargetCount()) /  Parameters.stepsProfiles.getTotalTargetCount();
        this.initialBalance = BalancesClients.pickNextBalance(paySim.random);
        this.balance = initialBalance;
        this.overdraftLimit = pickOverdraftLimit(paySim.random);

        this.city = ClientProfile.cities[new Random().nextInt(ClientProfile.cities.length)];
        this.country = ClientProfile.city2Country.get(this.city);
        this.ips = ClientProfile.generateIPs(5);  // generate 5 ips for use
        this.equip_ids = ClientProfile.createFingerprints();

        Random random = new Random();
        this.accounts.get(0).setBank(this.bank);
        this.accounts.get(0).setCardType(CARD_TYPES[random.nextInt(CARD_TYPES.length)]);
        // add other cards to client's accounts
        int numberOfAccounts = new Random().nextInt(MAX_CARD_NUM);
        for (int i=0; i<numberOfAccounts; i++) {
            Account account = new Account() ;
            account.setCardType(CARD_TYPES[random.nextInt(CARD_TYPES.length)]);
            account.setBank(this.bank);
            this.accounts.add(account);
        }
    }

    @Override
    public void step(SimState state) {
        PaySim paySim = (PaySim) state;
        int stepTargetCount = paySim.getStepTargetCount();
        if (stepTargetCount > 0) {
            MersenneTwisterFast random = paySim.random;
            int step = (int) state.schedule.getSteps();
            Map<String, Double> stepActionProfile = paySim.getStepProbabilities();

            // use binomial distribution to pick a count number
            int count = pickCount(random, stepTargetCount);

            // in one step take count times actions, and randomly pick action type,
            // amount, actionProfile
            for (int t = 0; t < count; t++) {
                String action = pickAction(random, stepActionProfile);
                StepActionProfile stepAmountProfile = paySim.getStepAction(action);
                double amount = pickAmount(random, action, stepAmountProfile);

                makeTransaction(paySim, step, action, amount);
            }
        }
    }

    public String[] getEquipIds() {
        return this.equip_ids;
    }

    public String[] getIPs() {
        return this.ips;
    }

    private int pickCount(MersenneTwisterFast random, int targetStepCount) {
        // B(n,p): n = targetStepCount & p = clientWeight, generate nextInt times to action
        Binomial transactionNb = new Binomial(targetStepCount, clientWeight, random);
        return transactionNb.nextInt();
    }

    private Account pickAccount(ArrayList<Account> accounts) {
        return accounts.get(new Random().nextInt(accounts.size()));
    }

    private String pickAction(MersenneTwisterFast random, Map<String, Double> stepActionProb) {
        Map<String, Double> clientProbabilities = clientProfile.getActionProbability();
        Map<String, Double> rawProbabilities = new HashMap<>();
        RandomCollection<String> actionPicker = new RandomCollection<>(random);

        // Pick the compromise between the Step distribution and the Client distribution
        for (Map.Entry<String, Double> clientEntry : clientProbabilities.entrySet()) {
            String action = clientEntry.getKey();
            double clientProbability = clientEntry.getValue();
            double rawProbability;

            if (stepActionProb.containsKey(action)) {
                double stepProbability = stepActionProb.get(action);

                rawProbability = (clientProbability + stepProbability) / 2;
            } else {
                rawProbability = clientProbability;
            }
            rawProbabilities.put(action, rawProbability);
        }

        // Correct the distribution so the balance of the account do not diverge too much
        double probInflow = 0;
        for (Map.Entry<String, Double> rawEntry : rawProbabilities.entrySet()) {
            String action = rawEntry.getKey();
            if (isInflow(action)) {
                probInflow += rawEntry.getValue();
            }
        }
        double probOutflow = 1 - probInflow;
        double newProbInflow = computeProbWithSpring(probInflow, probOutflow, balance);
        double newProbOutflow = 1 - newProbInflow;

        for (Map.Entry<String, Double> rawEntry : rawProbabilities.entrySet()) {
            String action = rawEntry.getKey();
            double rawProbability = rawEntry.getValue();
            double finalProbability;

            if (isInflow(action)) {
                finalProbability = rawProbability * newProbInflow / probInflow;
            } else {
                finalProbability = rawProbability * newProbOutflow / probOutflow;
            }
            actionPicker.add(finalProbability, action);
        }

        return actionPicker.next();
    }

    /**
     *  The Biased Bernoulli Walk we were doing can go far to the equilibrium of an account
     *  To avoid this we conceptually add a spring that would be attached to the equilibrium position of the account
     */
    private double computeProbWithSpring(double probUp, double probDown, double currentBalance){
        double equilibrium = 40 * expectedAvgTransaction; // Could also be the initial balance in other models
        double correctionStrength = 3 * Math.pow(10, -5); // In a physical model it would be 1 / 2 * kB * T
        double characteristicLengthSpring = equilibrium;
        double k = 1 / characteristicLengthSpring;
        double springForce = k * (equilibrium - currentBalance);
        double newProbUp = 0.5d * ( 1d + (expectedAvgTransaction * correctionStrength) * springForce + (probUp - probDown));

        if (newProbUp > 1){
           newProbUp = 1;
        } else if (newProbUp < 0){
            newProbUp = 0;
        }
        return newProbUp;

    }

    private boolean isInflow(String action){
        String[] inflowActions = {CASH_IN, DEPOSIT};
        return Arrays.stream(inflowActions)
                .anyMatch(action::equals);
    }

    private double pickAmount(MersenneTwisterFast random, String action, StepActionProfile stepAmountProfile) {
        ClientActionProfile clientAmountProfile = clientProfile.getProfilePerAction(action);

        double average, std;
        if (stepAmountProfile != null) {
            // We take the mean between the two distributions
            average = (clientAmountProfile.getAvgAmount() + stepAmountProfile.getAvgAmount()) / 2;
            std = Math.sqrt((Math.pow(clientAmountProfile.getStdAmount(), 2) + Math.pow(stepAmountProfile.getStdAmount(), 2))) / 2;
        } else {
            average = clientAmountProfile.getAvgAmount();
            std = clientAmountProfile.getStdAmount();
        }

        double amount = -1;
        while (amount <= 0) {
            amount = random.nextGaussian() * std + average;
        }

        return amount;
    }

    //TODO: the following case should be formulated into same structure and use Handlers.
    private void makeTransaction(PaySim state, int step, String action, double amount) {
        //find a random equip ip
        Random random = new Random();
        String equip_ip = equip_ids[random.nextInt(equip_ids.length)];
        switch (action) {
            case CASH_IN:
                handleCashIn(state, step, amount, equip_ip);
                break;
            case CASH_OUT:
                handleCashOut(state, step, amount, equip_ip);
                break;
            case DEBIT:
                handleDebit(state, step, amount, equip_ip);
                break;
            case PAYMENT:
                handlePayment(state, step, amount, equip_ip);
                break;
            // For transfer transaction there is a limit so we have to split big transactions in smaller chunks
            case TRANSFER:
                Client clientTo = state.pickRandomClient(getName());
                double reducedAmount = amount;
                boolean lastTransferFailed = false;
                while (reducedAmount > Parameters.transferLimit && !lastTransferFailed) {
                    lastTransferFailed = !handleTransfer(state, step, Parameters.transferLimit, equip_ip, clientTo);
                    reducedAmount -= Parameters.transferLimit;
                }
                if (reducedAmount > 0 && !lastTransferFailed) {
                    handleTransfer(state, step, reducedAmount, equip_ip, clientTo);
                }
                break;
            case DEPOSIT:
                handleDeposit(state, step, amount, equip_ip);
                break;
            default:
                throw new UnsupportedOperationException("Action not implemented in Client");
        }
    }

    // the client deposit cash in, without transfer or payment
    protected void handleCashIn(PaySim paysim, int step, double amount, String equip_ip) {
        Merchant merchantTo = paysim.pickRandomMerchant();
        String nameOrig = this.getName();
        String nameDest = merchantTo.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = merchantTo.getBalance();

        this.deposit(amount);

        double newBalanceOrig = this.getBalance();
        double newBalanceDest = merchantTo.getBalance();

        String ip = this.ips[new Random().nextInt(this.ips.length)];

        Account orgAccount = pickAccount(this.accounts) ;
        Account desAccount = pickAccount(merchantTo.accounts);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        Transaction t = new Transaction(step, CASH_IN, amount, nameOrig, oldBalanceOrig,
                newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                this.country, ip, equip_ip, isInnerTransaction,
                orgAccount.isPublic, desAccount.isPublic,
                orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

        paysim.getTransactions().add(t);
    }

    // means the client take cash out
    protected void handleCashOut(PaySim paysim, int step, double amount, String equip_ip) {
        Merchant merchantTo = paysim.pickRandomMerchant();
        String nameOrig = this.getName();
        String nameDest = merchantTo.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = merchantTo.getBalance();

        boolean isUnauthorizedOverdraft = this.withdraw(amount);

        double newBalanceOrig = this.getBalance();
        double newBalanceDest = merchantTo.getBalance();

        String ip = this.ips[new Random().nextInt(this.ips.length)];
        Account orgAccount = pickAccount(this.accounts) ;
        Account desAccount = pickAccount(merchantTo.accounts);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        Transaction t = new Transaction(step, CASH_OUT, amount, nameOrig, oldBalanceOrig,
                newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                this.country, ip, equip_ip, isInnerTransaction,
                orgAccount.isPublic, desAccount.isPublic,
                orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

        t.setUnauthorizedOverdraft(isUnauthorizedOverdraft);
        t.setFraud(this.isFraud());
        paysim.getTransactions().add(t);
    }

    protected void handleDebit(PaySim paysim, int step, double amount, String equip_ip) {
        String nameOrig = this.getName();
        String nameDest = this.bank.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = this.bank.getBalance();

        boolean isUnauthorizedOverdraft = this.withdraw(amount);

        double newBalanceOrig = this.getBalance();
        double newBalanceDest = this.bank.getBalance();

        String ip = this.ips[new Random().nextInt(this.ips.length)];
        Account orgAccount = pickAccount(this.accounts) ;
        Account desAccount = pickAccount(this.bank.accounts);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        Transaction t = new Transaction(step, DEBIT, amount, nameOrig, oldBalanceOrig,
                newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                this.country, ip, equip_ip, isInnerTransaction,
                orgAccount.isPublic, desAccount.isPublic,
                orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

        t.setUnauthorizedOverdraft(isUnauthorizedOverdraft);
        paysim.getTransactions().add(t);
    }

    protected void handlePayment(PaySim paysim, int step, double amount, String equip_ip) {
        Merchant merchantTo = paysim.pickRandomMerchant();

        String nameOrig = this.getName();
        String nameDest = merchantTo.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = merchantTo.getBalance();

        boolean isUnauthorizedOverdraft = this.withdraw(amount);
        if (!isUnauthorizedOverdraft) {
            merchantTo.deposit(amount);
        }

        double newBalanceOrig = this.getBalance();
        double newBalanceDest = merchantTo.getBalance();

        String ip = this.ips[new Random().nextInt(this.ips.length)];
        Account orgAccount = pickAccount(this.accounts) ;
        Account desAccount = pickAccount(merchantTo.accounts);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        Transaction t = new Transaction(step, PAYMENT, amount, nameOrig, oldBalanceOrig,
                newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                this.country, ip, equip_ip, isInnerTransaction,
                orgAccount.isPublic, desAccount.isPublic,
                orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

        t.setUnauthorizedOverdraft(isUnauthorizedOverdraft);
        paysim.getTransactions().add(t);
    }

    protected boolean handleTransfer(PaySim paysim, int step, double amount,
                                     String equip_ip, Client clientTo) {
        String nameOrig = this.getName();
        String nameDest = clientTo.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = clientTo.getBalance();
        String ip = this.ips[new Random().nextInt(this.ips.length)];
        Account orgAccount = pickAccount(this.accounts) ;
        Account desAccount = pickAccount(clientTo.accounts);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        boolean transferSuccessful;
        if (!isDetectedAsFraud(amount)) {
            boolean isUnauthorizedOverdraft = this.withdraw(amount);
            transferSuccessful = !isUnauthorizedOverdraft;
            if (transferSuccessful) {
                clientTo.deposit(amount);
            }

            double newBalanceOrig = this.getBalance();
            double newBalanceDest = clientTo.getBalance();

            Transaction t = new Transaction(step, TRANSFER, amount, nameOrig, oldBalanceOrig,
                    newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                    this.country, ip, equip_ip, isInnerTransaction,
                    orgAccount.isPublic, desAccount.isPublic,
                    orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

            t.setUnauthorizedOverdraft(isUnauthorizedOverdraft);
            t.setFraud(this.isFraud());
            paysim.getTransactions().add(t);
        } else { // create the transaction but don't move any money as the transaction was detected as fraudulent
            transferSuccessful = false;
            double newBalanceOrig = this.getBalance();
            double newBalanceDest = clientTo.getBalance();

            Transaction t = new Transaction(step, TRANSFER, amount, nameOrig, oldBalanceOrig,
                    newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                    this.country, ip, equip_ip, isInnerTransaction,
                    orgAccount.isPublic, desAccount.isPublic,
                    orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

            t.setFlaggedFraud(true);
            t.setFraud(this.isFraud());
            paysim.getTransactions().add(t);
        }
        return transferSuccessful;
    }

    protected void handleDeposit(PaySim paysim, int step, double amount, String equip_ip) {
        String nameOrig = this.getName();
        String nameDest = this.bank.getName();
        double oldBalanceOrig = this.getBalance();
        double oldBalanceDest = this.bank.getBalance();

        this.deposit(amount);

        double newBalanceOrig = this.getBalance();
        double newBalanceDest = this.bank.getBalance();
        String ip = this.ips[new Random().nextInt(this.ips.length)];
        Account orgAccount = pickAccount(this.accounts) ;
        Account desAccount = pickAccount(this.bank.accounts);
        boolean isInnerTransaction = (orgAccount.bankName == desAccount.bankName);

        Transaction t = new Transaction(step, DEPOSIT, amount, nameOrig, oldBalanceOrig,
                newBalanceOrig, nameDest, oldBalanceDest, newBalanceDest, this.city,
                this.country, ip, equip_ip, isInnerTransaction,
                orgAccount.isPublic, desAccount.isPublic,
                orgAccount.accountNumber, desAccount.accountNumber, orgAccount.cardType);

        paysim.getTransactions().add(t);
    }

    private boolean isDetectedAsFraud(double amount) {
        boolean isFraudulentAccount = false;
        if (this.countTransferTransactions >= MIN_NB_TRANSFER_FOR_FRAUD) {
            if (this.balanceMax - this.balance - amount > Parameters.transferLimit * 2.5) {
                isFraudulentAccount = true;
            }
        } else {
            this.countTransferTransactions++;
            this.balanceMax = max(this.balanceMax, this.balance);
        }
        return isFraudulentAccount;
    }

    private double pickOverdraftLimit(MersenneTwisterFast random){
        double stdTransaction = 0;

        for (String action: ActionTypes.getActions()){
            double actionProbability = clientProfile.getActionProbability().get(action);
            ClientActionProfile actionProfile = clientProfile.getProfilePerAction(action);
            expectedAvgTransaction += actionProfile.getAvgAmount() * actionProbability;
            stdTransaction += Math.pow(actionProfile.getStdAmount() * actionProbability, 2);
        }
        stdTransaction = Math.sqrt(stdTransaction);

        double randomizedMeanTransaction = random.nextGaussian() * stdTransaction + expectedAvgTransaction;

        return BalancesClients.getOverdraftLimit(randomizedMeanTransaction);
    }
}
