import java.util.ArrayList;
import java.util.HashMap;

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   private static final UTXOPool unspentTransactions = new UTXOPool();
   
   private ArrayList<Link> chain  = new ArrayList<Link>();
   private final HashMap<ByteArrayWrapper, Link> hashToNodeMap = new HashMap<ByteArrayWrapper, Link>();
   private final TransactionPool transactionsWaitingForNewBlock  = new TransactionPool();
   private int height;
   private Link maxHeightBlock; 
   
   /* create an empty block chain with just a genesis block.
    * Assume genesis block is a valid block
    */
   public BlockChain(Block genesisBlock) {
      Transaction coinbase = genesisBlock.getCoinbase();
      unspentTransactions.addUTXO(new UTXO(coinbase.getHash(), 0), coinbase.getOutput(0));
      Link genesis = new Link(genesisBlock, null, unspentTransactions);
      chain.add(genesis);
      hashToNodeMap.put(new ByteArrayWrapper(genesisBlock.getHash()), genesis);
      height = 1;
      maxHeightBlock = genesis;
   }

   /* Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      return maxHeightBlock.block;
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
      return transactionsWaitingForNewBlock;
   }

   /* Return the block node if its height >= (maxHeight - CUT_OFF_AGE) 
    * so we can search for genesis block (height = 1) as long as height of
    * block chain <= (CUT_OFF_AGE + 1).
    * So if max height is (CUT_OFF_AGE + 2), search for genesis block
    * will return null
    */
   private Link getPreviousBlock(Block block) {
      byte[] prevBlockHash = block.getPrevBlockHash();
      if (prevBlockHash == null) {
        return null;
      }
      return hashToNodeMap.get(new ByteArrayWrapper(prevBlockHash));
   }

   /* Add a block to block chain if it is valid.
    * For validity, all transactions should be valid
    * and block should be at height > (maxHeight - CUT_OFF_AGE).
    * For example, you can try creating a new block over genesis block 
    * (block height 2) if blockChain height is <= CUT_OFF_AGE + 1. 
    * As soon as height > CUT_OFF_AGE + 1, you cannot create a new block at height 2.
    * Return true of block is successfully added
    */
   public boolean addBlock(Block block) {
      Link previousBlock = getPreviousBlock(block);
      if (previousBlock != null) {
        TxHandler handler = new TxHandler(previousBlock.getUTXOPoolCopy());
        Transaction[] validTransactions = handler.handleTxs(block.getTransactions().toArray(new Transaction[0]));
        if (validTransactions.length == block.getTransactions().size()) {
          UTXOPool unspentTransactions = handler.getUTXOPool();
          Transaction coinbase = block.getCoinbase();
          UTXO utxoCoinbase = new UTXO(coinbase.getHash(), 0);
          unspentTransactions.addUTXO(utxoCoinbase, coinbase.getOutput(0));
    
          for (Transaction transactionAlreadyOnABlock : block.getTransactions()) {
            transactionsWaitingForNewBlock.removeTransaction(transactionAlreadyOnABlock.getHash());
          }
    
          Link current = new Link(block, previousBlock, unspentTransactions);
          hashToNodeMap.put(new ByteArrayWrapper(block.getHash()), current);
          if (current.height > height) {
             maxHeightBlock = current;
             height = current.height;
          }
          if (height - chain.get(0).height > CUT_OFF_AGE) {
            ArrayList<Link> newNodes = new ArrayList<Link>();
            for (Link link : chain) {
              for (Link child : link.children) {
                newNodes.add(child);
              }
              hashToNodeMap.remove(new ByteArrayWrapper(link.block.getHash()));
            }
            chain = newNodes;
          }
          return true;  
        }       
      }
      return false;
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      transactionsWaitingForNewBlock.addTransaction(tx);
   }

   private class Link {
    public Block block;
    public Link parent;
    public ArrayList<Link> children;
    public int height;
    private UTXOPool unspentTransactions;

    public Link(Block block, Link parent, UTXOPool unspentTransactions) {
       this.block = block;
       this.parent = parent;
       children = new ArrayList<Link>();
       this.unspentTransactions = unspentTransactions;
       if (parent != null) {
          height = parent.height + 1;
          parent.children.add(this);
       } else {
          height = 1;
       }
    }

    public UTXOPool getUTXOPoolCopy() {
       return new UTXOPool(unspentTransactions);
    }
 }
}
