package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.utils.ClientUtils;
import java8.util.Optional;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.LoggerFactory;

public enum WhirlpoolServer {
  TEST(
      "pool.whirl.mx:8081",
      "udkmfc5j6zvv3ysavbrwzhwji4hpyfe3apqa6yst7c7l32mygf65g4ad.onion",
      TestNet3Params.get(),
      true,
      "q5ikHzegh1fVs0YekhV6IbeM1YVi6mW9txoHpqPVGDQ+XcYlusvZfTRDQjt3LVOgTNXrlbs4pjlDqHVk38L2/1rGRVposOVFopX2rpm0zIfcXLdb8ymahhqCzje6Lb6HUpx3nQRQd6uL3eG3+XFo;3ejRfQL51G+l4iFE2UMYU+Ti7PIWmAjE015Qy/SYcnxMJYoX14maJwxyAH8HGgmSHayo8u90/HoxxjcUrbeQijWTDzs5xdQfx/yk2MzfuuONCPkqpR/y1SO0mVXYe+TgJfkx5V0CRvnThIr1mjoc"),
  INTEGRATION(
      "pool.whirl.mx:8082",
      null,
      TestNet3Params.get(),
      true,
      "q5ikHzegh1fVs0YekhV6IbeM1YVi6mW9txoHpqPVGDQ+XcYlusvZfTRDQjt3LVOgTNXrlbs4pjlDqHVk38L2/1rGRVposOVFopX2rpm0zIfcXLdb8ymahhqCzje6Lb6HUpx3nQRQd6uL3eG3+XFo;3ejRfQL51G+l4iFE2UMYU+Ti7PIWmAjE015Qy/SYcnxMJYoX14maJwxyAH8HGgmSHayo8u90/HoxxjcUrbeQijWTDzs5xdQfx/yk2MzfuuONCPkqpR/y1SO0mVXYe+TgJfkx5V0CRvnThIr1mjoc"),
  MAIN(
      "pool.whirl.mx:8080",
      null,
      MainNetParams.get(),
      true,
      "XRK4tP3cUnmvH5fGaQFO2Kb2uIGvoERCyWpqdERjZiPtAVhl8fHgA+fpRQ04d7WJ/xM+uOpnuKWfmPxys4pYtjmYbJec2GKNdTV738S+A5IVW7A+z7OehiNvMi3TTEGRViYzQLY70Z7DDfEXUNo6;J2LN1suuYwreU/S/BDAvu+TPwLbakTQoniQQQxUEJVWpQxQmiZmCSdGKJ3hoOvHNkStmz4cyyfCp6bRL58cg7A/UD8Kpkgz4QEwrhZX1Yeh9CMBp94bsyhA/Z0OWKzvoPl9rN/Jet6e6aLlNI7dp"),
  LOCAL_TEST(
      "127.0.0.1:8080",
      null,
      TestNet3Params.get(),
      false,
      "q5ikHzegh1fVs0YekhV6IbeM1YVi6mW9txoHpqPVGDQ+XcYlusvZfTRDQjt3LVOgTNXrlbs4pjlDqHVk38L2/1rGRVposOVFopX2rpm0zIfcXLdb8ymahhqCzje6Lb6HUpx3nQRQd6uL3eG3+XFo;3ejRfQL51G+l4iFE2UMYU+Ti7PIWmAjE015Qy/SYcnxMJYoX14maJwxyAH8HGgmSHayo8u90/HoxxjcUrbeQijWTDzs5xdQfx/yk2MzfuuONCPkqpR/y1SO0mVXYe+TgJfkx5V0CRvnThIr1mjoc");

  private String serverUrl;
  private String serverOnion;
  private NetworkParameters params;
  private boolean ssl;
  private String feeData;

  WhirlpoolServer(
      String serverUrl, String serverOnion, NetworkParameters params, boolean ssl, String feeData) {
    this.serverUrl = serverUrl;
    this.serverOnion = serverOnion;
    this.params = params;
    this.ssl = ssl;
    try {
      this.feeData = ClientUtils.decodeFeeData(feeData);
    } catch (Exception e) {
      LoggerFactory.getLogger(WhirlpoolServer.class).error("", e);
    }
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public String getServerOnion() {
    return serverOnion;
  }

  public NetworkParameters getParams() {
    return params;
  }

  public boolean isSsl() {
    return ssl;
  }

  public String getFeeData() {
    return feeData;
  }

  public static Optional<WhirlpoolServer> find(String value) {
    try {
      return Optional.of(valueOf(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
