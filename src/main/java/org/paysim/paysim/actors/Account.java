package org.paysim.paysim.actors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.shaded.jackson.databind.JsonSerializer;

import java.util.Random;

public class Account {
    protected String accountOwnerId;
    protected String accountNumber;
    protected Boolean isPublic = false;
    protected String bankName;
    protected String cardType="";
    protected Double balance=0.0;

    Account() {
        this.accountNumber = generateCardNumber();
    }

    public void setPublic(Boolean isPublicAccount) {
        this.isPublic = isPublicAccount;
    }

    public void setBank(Bank bank) {
        this.bankName = bank.getName();
    }

    public void setCardType(String type){
        this.cardType = type;
    }

    public String generateCardNumber() {
        Random random = new Random();

        Integer prev = 622126 + random.nextInt(925 + 1 - 126);
        String bardNo = prev + StringUtils.leftPad(
                random.nextInt(999999999) + "", 9, "0");
        char[] chs = bardNo.trim().toCharArray();
        int luhmSum = 0;
        for (int i = chs.length - 1, j = 0; i >= 0; i--, j++) {
            int k = chs[i] - '0';
            if (j % 2 == 0) {
                k *= 2;
                k = k / 10 + k % 10;
            }
            luhmSum += k;
        }
        char checkCode = luhmSum % 10 == 0 ? '0'
                : (char) (10 - luhmSum % 10 + '0');
        return bardNo + checkCode;
    }

    @Override
    public String toString(){
        //card_number,card_type,is_public,bank_name
        String[] s = {this.accountNumber, this.cardType, this.isPublic.toString(), this.bankName};
        return String.join(",", s) ;
    }
}