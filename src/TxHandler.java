import java.util.ArrayList;
import java.util.Collections;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        Crypto crypto = new Crypto();
        int outputSum = 0;
        int inputSum = 0;
        for (int index = 0; index < tx.numInputs(); index++) {
            UTXO utxo = new UTXO(tx.getInput(index).prevTxHash, tx.getInput(index).outputIndex);
            inputSum += utxoPool.getTxOutput(utxo).value;

            if (!crypto.verifySignature(utxoPool.getTxOutput(utxo).address, tx.getRawDataToSign(index), tx.getInput(index).signature)) {
                return false;
            }
            if (!utxoPool.contains(utxo)) {
                return false;
            }
            for (int nextIndex = index + 1; nextIndex < tx.numInputs(); nextIndex++) {
                if (!tx.getInput(index).equals(tx.getInput(nextIndex))) {
                    return false;
                }
            }
        }
        for (int i = 0; i < tx.numOutputs(); i++) {
            if (tx.getOutput(i).value < 0) {
                return false;
            } else {
                outputSum += tx.getOutput(i).value;
            }
        }
        if (inputSum < outputSum) {
            return false;
        }
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> possibleTransactions = new ArrayList<>();
        Collections.addAll(possibleTransactions, possibleTxs);
        ArrayList<Transaction> validTransactions = new ArrayList<>();
        ArrayList<Integer> toBeDeleted = new ArrayList<>();
        int txIndex = 0;
        boolean noChange = false;
        while (possibleTransactions.size() > 0 && !noChange) {
            if (isValidTx(possibleTransactions.get(txIndex))) {
                validTransactions.add(possibleTransactions.get(txIndex));
                toBeDeleted.add(txIndex);
                for (int inputIndex = 0; inputIndex < possibleTransactions.get(txIndex).numInputs(); inputIndex++) {
                    UTXO toBeRemovedUtxo = new UTXO(possibleTransactions.get(txIndex).getInput(inputIndex).prevTxHash,
                            possibleTransactions.get(txIndex).getInput(inputIndex).outputIndex);
                    utxoPool.removeUTXO(toBeRemovedUtxo);
                }
                for (int outputIndex = 0; outputIndex < possibleTransactions.get(txIndex).numOutputs(); outputIndex++) {
                    UTXO toBeAddedUtxo = new UTXO(possibleTransactions.get(txIndex).getHash(), outputIndex);
                    utxoPool.addUTXO(toBeAddedUtxo, possibleTransactions.get(txIndex).getOutput(outputIndex));
                }
            }
            txIndex++;
            if (txIndex >= possibleTransactions.size()) {
                txIndex = 0;
                if (toBeDeleted.size() > 0) {
                    for (int i = 0; i < toBeDeleted.size(); i++) {
                        possibleTransactions.remove((int) toBeDeleted.get(i));
                    }
                    toBeDeleted.clear();
                } else {
                    noChange = true;
                }
            }
        }
        Transaction[] validTxs = new Transaction[validTransactions.size()];
        validTransactions.toArray(validTxs);
        return validTxs;
    }

    public UTXOPool getUtxoPool() {
        return utxoPool;
    }
}
