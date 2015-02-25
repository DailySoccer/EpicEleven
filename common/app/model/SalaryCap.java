package model;


import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.util.Arrays;


public enum SalaryCap {
    EASY(75000),
    STANDARD(70000),
    DIFFICULT(65000);

    public final int money;

    SalaryCap(int money) {
        this.money = money;
    }


    public static SalaryCap findByMoney(int money) {
        return Iterables.find(Arrays.asList(values()),
                new Predicate<SalaryCap>() {
                    public boolean apply(SalaryCap input) {
                        return input.money ==  money;
                    }
                }, null);
    }
}