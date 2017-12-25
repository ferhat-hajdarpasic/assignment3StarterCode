import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Node;

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   private ArrayList<Node> heads;
   private HashMap<ByteArrayWrapper, Node> H;
   private int height;
   private Node maxHeightBlock; 
   private TransactionPool txPool;

   /* create an empty block chain with just a genesis block.
    * Assume genesis block is a valid block
    */
   public BlockChain(Block genesisBlock) {
      UTXOPool uPool = new UTXOPool();
      Transaction coinbase = genesisBlock.getCoinbase();
      UTXO utxoCoinbase = new UTXO(coinbase.getHash(), 0);
      uPool.addUTXO(utxoCoinbase, coinbase.getOutput(0));
      Node genesis = new Node(genesisBlock, null, uPool);
      heads = new ArrayList<Node>();
      heads.add(genesis);
      H = new HashMap<ByteArrayWrapper, Node>();
      H.put(new ByteArrayWrapper(genesisBlock.getHash()), genesis);
      height = 1;
      maxHeightBlock = genesis;
      txPool = new TransactionPool();
   }

   /* Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      return maxHeightBlock.b;
   }
   
   /* Get the UTXOPool for mining a new block on top of 
    * max height block
    */
   public UTXOPool getMaxHeightUTXOPool() {
      return maxHeightBlock.getUTXOPoolCopy();
   }
   
   /* Get the transaction pool to mine a new block
    */
   public TransactionPool getTransactionPool() {
      return txPool;
   }

   /* Return the block node if its height >= (maxHeight - CUT_OFF_AGE) 
    * so we can search for genesis block (height = 1) as long as height of
    * block chain <= (CUT_OFF_AGE + 1).
    * So if max height is (CUT_OFF_AGE + 2), search for genesis block
    * will return null
    */
   private Node getBlock(byte[] blockHash) {
      ByteArrayWrapper hash = new ByteArrayWrapper(blockHash);
      return H.get(hash);
   }

   /* Check if the transactions of the block form a valid set of transactions 
    * corresponding to the utxo pool (similar validity as in Assignment 1)
    * If its a valid set, return the updated utxo pool 
    * and add the block's coinbase transaction to it.
    * If not a valid set, return null
    */
   private UTXOPool processBlockTxs(UTXOPool uPool, Block b) {
      ArrayList<Transaction> aTxs = b.getTransactions();
      Transaction[] txs = aTxs.toArray(new Transaction[0]);

      TxHandler handler = new TxHandler(uPool);
      Transaction[] rTxs = handler.handleTxs(txs);
      if (rTxs.length != txs.length)
         return null;
      
      uPool = handler.getUTXOPool();
      Transaction coinbase = b.getCoinbase();
      UTXO utxoCoinbase = new UTXO(coinbase.getHash(), 0);
      uPool.addUTXO(utxoCoinbase, coinbase.getOutput(0));
      return uPool;
   }

   /* update the transaction pool, removing transactions used by block and return it
    */
   private void updateTransactionPool(Block b) {
      ArrayList<Transaction> aTxs = b.getTransactions();
      for (Transaction tx : aTxs) {
         txPool.removeTransaction(tx.getHash());
      }
   }

   /* Add a block to block chain if it is valid.
    * For validity, all transactions should be valid
    * and block should be at height > (maxHeight - CUT_OFF_AGE).
    * For example, you can try creating a new block over genesis block 
    * (block height 2) if blockChain height is <= CUT_OFF_AGE + 1. 
    * As soon as height > CUT_OFF_AGE + 1, you cannot create a new block at height 2.
    * Return true of block is successfully added
    */
   public boolean addBlock(Block b) {
      byte[] prevBlock = b.getPrevBlockHash();
      if (prevBlock == null) {
         return false;
      }
      Node parent = getBlock(prevBlock);
      if (parent == null)
         return false;
      UTXOPool uPool = parent.getUTXOPoolCopy();
      uPool = processBlockTxs(uPool, b);
      if (uPool == null)
         return false;
      updateTransactionPool(b);

      Node current = new Node(b, parent, uPool);
      H.put(new ByteArrayWrapper(b.getHash()), current);
      if (current.height > height) {
         maxHeightBlock = current;
         height = current.height;
      }
      if (height - heads.get(0).height > CUT_OFF_AGE) {
         ArrayList<Node> newHeads = new ArrayList<Node>();
         for (Node bHeads : heads) {
            for (Node bChild : bHeads.children) {
               newHeads.add(bChild);
            }
            H.remove(new ByteArrayWrapper(bHeads.b.getHash()));
         }
         heads = newHeads;
      }
      return true;
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      txPool.addTransaction(tx);
   }

   private class Node {
    public Block b;
    public Node parent;
    public ArrayList<Node> children;
    public int height;
    private UTXOPool uPool;

    public Node(Block b, Node parent, UTXOPool uPool) {
       this.b = b;
       this.parent = parent;
       children = new ArrayList<Node>();
       this.uPool = uPool;
       if (parent != null) {
          height = parent.height + 1;
          parent.children.add(this);
       } else {
          height = 1;
       }
    }

    public UTXOPool getUTXOPoolCopy() {
       return new UTXOPool(uPool);
    }
 }


}
