package com.samourai.whirlpool.client.wallet;

public class WhirlpoolWalletState {
  private int nbSuccess;
  private int nbError;

  public WhirlpoolWalletState() {
    this.nbSuccess = 0;
    this.nbError = 0;
  }

  public int getNbSuccess() {
    return nbSuccess;
  }

  public void setNbSuccess(int nbSuccess) {
    this.nbSuccess = nbSuccess;
  }

  public int getNbError() {
    return nbError;
  }

  public void setNbError(int nbError) {
    this.nbError = nbError;
  }
}
