// The BlockChain class should maintain only limited block nodes to satisfy the functionality.
// You should not have all the blocks added to the block chain in memory
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    private Node maxHeightNode = null;

    private class Node {
        public Block block;
        public int height;
        public UTXOPool utxoPool;
        public TransactionPool transactionPool;
        public byte[] parent;
        public ArrayList<byte[]> children;

        public Node(Block block, int height, UTXOPool utxoPool, TransactionPool transactionPool, byte[] parent) {
            this.block = block;
            this.height = height;
            this.utxoPool = utxoPool;
            this.transactionPool = transactionPool;
            this.parent = parent;
        }
    }

    private HashMap<byte[], Node> blockChain;
    private TransactionPool transactionPool;
    private ArrayList<Node> headNodes;


    /**
     * create an empty blockchain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        transactionPool = new TransactionPool();
        transactionPool.addTransaction(genesisBlock.getCoinbase());
        UTXOPool utxoPool = new UTXOPool();
        utxoPool.addUTXO(new UTXO(genesisBlock.getCoinbase().getHash(), 0), genesisBlock.getCoinbase().getOutput(0));
        Node node = new Node(genesisBlock, 1, utxoPool, transactionPool, null);
        blockChain = new HashMap<>();
        blockChain.put(node.block.getHash(), node);
        headNodes = new ArrayList<>();
        headNodes.add(node);
        maxHeightNode = node;
    }

    /**
     * Get the maximum height block
     */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /**
     * Get the UTXOPool for mining a new block on top of max height block
     */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.utxoPool;
    }

    /**
     * Get the transaction pool to mine a new block
     */
    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    /**
     * Add {@code block} to the blockchain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}, where maxHeight is
     * the current height of the blockchain.
     * <p>
     * Assume the Genesis block is at height 1.
     * For example, you can try creating a new block over the genesis block (i.e. create a block at
     * height 2) if the current blockchain height is less than or equal to CUT_OFF_AGE + 1. As soon as
     * the current blockchain height exceeds CUT_OFF_AGE + 1, you cannot create a new block at height 2.
     *
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block == null) {
            return false;
        }

        if (block.getPrevBlockHash() == null) {
            return false;
        }

        if (!blockChain.containsKey(block.getPrevBlockHash())) {
            return false;
        }

        Node node = blockChain.get(block.getPrevBlockHash());
        int numOfTxs = block.getTransactions().size();
        int height = node.height + 1;

        if (height <= (maxHeightNode.height - CUT_OFF_AGE)) {
            return false;
        }

        TxHandler txHandler = new TxHandler(node.utxoPool);
        Transaction[] validTxs = txHandler.handleTxs(block.getTransactions().toArray(new Transaction[0]));

        if (validTxs.length != numOfTxs) {
            return false;
        }

        transactionPool = new TransactionPool(node.transactionPool);
        for (int index = 0; index < numOfTxs; index++) {
            transactionPool.removeTransaction(block.getTransaction(index).getHash());
        }
        transactionPool.addTransaction(block.getCoinbase());
        txHandler.getUtxoPool().addUTXO(new UTXO(block.getCoinbase().getHash(), 0), block.getCoinbase().getOutput(0));
        Node newNode = new Node(block, height, txHandler.getUtxoPool(), transactionPool, block.getPrevBlockHash());
        node.children = new ArrayList<>();
        node.children.add(block.getHash());
        blockChain.put(newNode.block.getHash(), newNode);

        if (height > maxHeightNode.height) {
            maxHeightNode = newNode;
        }

        if (maxHeightNode.height > CUT_OFF_AGE + 1) {
            int numOfHeads = headNodes.size();
            for (int headIndex = 0; headIndex < numOfHeads; headIndex++) {
                Node toBeRemoved = headNodes.get(headIndex);
                if(toBeRemoved.children != null) {
                    for (int childIndex = 0; childIndex < toBeRemoved.children.size(); childIndex++) {
                        Node child = blockChain.get(toBeRemoved.children.get(childIndex));
                        child.parent = null;
                        headNodes.add(child);
                    }
                    toBeRemoved.children.clear();
                }
                toBeRemoved.parent = null;
                headNodes.remove(headIndex);
                blockChain.remove(toBeRemoved.block.getHash());
            }
        }

        return true;
    }

    /**
     * Add a transaction to the transaction pool
     */
    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }
}