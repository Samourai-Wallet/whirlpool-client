package com.samourai.whirlpool.client.services;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.client.beans.PremixResult;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class PremixService {

    // premix results


    public PremixService() {

    }

    public PremixResult premix(NetworkParameters params, List<String> seedWords, String passphrase, MnemonicCode mc,
                               String utxoSpendFrom, int nbSpendTos, long swFee, long selectedAmount, long unitSpendAmount, long fee) throws Exception {
        byte[] seed = mc.toEntropy(seedWords);

        // init BIP44 wallet
        HD_Wallet hdw = new HD_Wallet(44, mc, params, seed, passphrase, 1);
        // init BIP84 wallet for input
        HD_Wallet hdw84 = new HD_Wallet(84, mc, params, Hex.decode(hdw.getSeedHex()), hdw.getPassphrase(), 1);
        // init BIP47 wallet for input
        BIP47Wallet bip47w = new BIP47Wallet(47, mc, params, Hex.decode(hdw.getSeedHex()), hdw.getPassphrase(), 1);

        // -------------------

        boolean isTestnet = FormatsUtil.getInstance().isTestNet(params);
        int feeIdx  = 0; // address index, in prod get index from Samourai API

        System.out.println("tx0: -------------------------------------------");

        // net miner's fee
        // fee = unitSpendAmount - unitReceiveAmount;

        BigInteger biSelectedAmount = BigInteger.valueOf(selectedAmount);
        BigInteger biUnitSpendAmount = BigInteger.valueOf(unitSpendAmount);
        BigInteger biSWFee = BigInteger.valueOf(swFee);
        BigInteger biChange = BigInteger.valueOf(selectedAmount - ((unitSpendAmount * nbSpendTos) + fee + swFee));

        Map<String,String> mixables = new HashMap<String,String>();

        Map<String, ECKey> toPrivKeys = new HashMap<String, ECKey>();
        Map<String, String> toUTXO = new HashMap<String, String>();

        //
        // tx0
        //
        String tx0spendFrom = new SegwitAddress(hdw84.getAccount(0).getChain(0).getAddressAt(0).getPubKey(), params).getBech32AsString();
        ECKey ecKeySpendFrom = hdw84.getAccount(0).getChain(0).getAddressAt(0).getECKey();
        System.out.println("tx0 spend address:" + tx0spendFrom);
        String tx0change = new SegwitAddress(hdw84.getAccount(0).getChain(1).getAddressAt(0).getPubKey(), params).getBech32AsString();
        System.out.println("tx0 change address:" + tx0change);

        String pcode = bip47w.getAccount(0).getPaymentCode();
        List<String> spendTos = new ArrayList<>();
        for(int j = 0; j < nbSpendTos; j++)   {
            String toAddress = new SegwitAddress(hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getPubKey(), params).getBech32AsString();
            toPrivKeys.put(toAddress, hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getECKey());
//                System.out.println("spend to:"  + toAddress + "," + hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getECKey().getPrivateKeyAsWiF(params));
            spendTos.add(toAddress);
            mixables.put(toAddress, pcode);
        }

        //
        // make tx:
        // 5 spendTo outputs
        // SW fee
        // change
        // OP_RETURN
        //
        List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
        Transaction tx = new Transaction(params);

        //
        // 5 spend outputs
        //
        for(int k = 0; k < spendTos.size(); k++)   {
            Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", (String)spendTos.get(k));
            byte[] scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());

            TransactionOutput txOutSpend = new TransactionOutput(params, null, Coin.valueOf(biUnitSpendAmount.longValue()), scriptPubKey);
            outputs.add(txOutSpend);
        }

        //
        // 1 change output
        //
        Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", tx0change);
        byte[] _scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
        TransactionOutput txChange = new TransactionOutput(params, null, Coin.valueOf(biChange.longValue()), _scriptPubKey);
        outputs.add(txChange);

        // derive fee address
        final String XPUB_FEES = "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt";
        DeterministicKey mKey = FormatsUtil.getInstance().createMasterPubKeyFromXPub(XPUB_FEES);
        DeterministicKey cKey = HDKeyDerivation.deriveChildKey(mKey, new ChildNumber(0, false)); // assume external/receive chain
        DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(feeIdx, false));
        ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
        String feeAddress = new SegwitAddress(feeECKey.getPubKey(), params).getBech32AsString();
        System.out.println("fee address:" + feeAddress);

        Script outputScript = ScriptBuilder.createP2WPKHOutputScript(feeECKey);
        TransactionOutput txSWFee = new TransactionOutput(params, null, Coin.valueOf(biSWFee.longValue()), outputScript.getProgram());
        outputs.add(txSWFee);

        // add OP_RETURN output
        byte[] idxBuf = ByteBuffer.allocate(4).putInt(feeIdx).array();
        Script op_returnOutputScript = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(idxBuf).build();
        TransactionOutput txFeeIdx = new TransactionOutput(params, null, Coin.valueOf(0L), op_returnOutputScript.getProgram());
        outputs.add(txFeeIdx);

        feeIdx++;   // go to next address index, in prod get index from Samourai API

        //
        //
        //
        // bech32 outputs
        //
        Collections.sort(outputs, new BIP69OutputComparator());
        for(TransactionOutput to : outputs) {
            tx.addOutput(to);
        }

        String[] s = utxoSpendFrom.split("-");

        Sha256Hash txHash = new Sha256Hash(Hex.decode(s[0]));
        TransactionOutPoint outPoint = new TransactionOutPoint(params, Long.parseLong(s[1]), txHash, Coin.valueOf(biSelectedAmount.longValue()));

        final Script segwitPubkeyScript = ScriptBuilder.createP2WPKHOutputScript(ecKeySpendFrom);
        tx.addSignedInput(outPoint, segwitPubkeyScript, ecKeySpendFrom);

        final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
        final String strTxHash = tx.getHashAsString();

        tx.verify();
        System.out.println(tx);
        System.out.println("tx hash:" + strTxHash);
        System.out.println("tx hex:" + hexTx + "\n");

        for(TransactionOutput to : tx.getOutputs())   {
            toUTXO.put(Hex.toHexString(to.getScriptBytes()), strTxHash + "-" + to.getIndex());
        }

        return new PremixResult(mixables, toPrivKeys, toUTXO, pcode, bip47w);
    }

}
