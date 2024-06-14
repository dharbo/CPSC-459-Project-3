import java.util.ArrayList;
import java.util.HashMap;

/* Block Chain should maintain only limited block nodes to satisfy the functions
   You should not have the all the blocks added to the block chain in memory 
   as it would overflow memory
 */

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   public HashMap<ByteArrayWrapper, BlockNode> blockMapping;

   private TransactionPool transactionPool;

   private BlockNode maxHeightBlock;

   // all information required in handling a block in block chain
   private class BlockNode {
      public Block b;
      public BlockNode parent;
      public ArrayList<BlockNode> children;
      public int height;
      // utxo pool for making a new block on top of this block
      private UTXOPool uPool;

      public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
         this.b = b;
         this.parent = parent;
         children = new ArrayList<BlockNode>();
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

   /* create an empty block chain with just a genesis block.
    * Assume genesis block is a valid block
    */
   public BlockChain(Block genesisBlock) {
      // Initialize member variables.
      blockMapping = new HashMap<>();
      transactionPool = new TransactionPool();
      UTXOPool utxoPool = new UTXOPool();

      // Add the coinbase transaction.
      Transaction coinbaseTx = genesisBlock.getCoinbase();
      transactionPool.addTransaction(coinbaseTx);
      for (int i = 0; i < coinbaseTx.numOutputs(); i++) {
            Transaction.Output txOutput = coinbaseTx.getOutput(i);
            UTXO utxo = new UTXO(coinbaseTx.getHash(), i);
            utxoPool.addUTXO(utxo, txOutput);
      }

      BlockNode genesisNode = new BlockNode(genesisBlock, null, utxoPool);
      blockMapping.put(new ByteArrayWrapper(genesisBlock.getHash()), genesisNode);
      maxHeightBlock = genesisNode;
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
      return transactionPool;
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
      // If previous block hash is null, it is the genesis block. If so, return false.
      if (b.getPrevBlockHash() == null) {
         return false;
      }

      // If the parent block does not exist, return false.
      BlockNode parentBlock = blockMapping.get(new ByteArrayWrapper(b.getPrevBlockHash()));
      if (parentBlock == null) {
         return false;
      }

      // Validate txs with TxHandler.
      TxHandler txHandler = new TxHandler(parentBlock.getUTXOPoolCopy());
      ArrayList<Transaction> txs = b.getTransactions();
      Transaction[] validTxs = txHandler.handleTxs(txs.toArray(new Transaction[txs.size()]));

      // If the lengths of validTxs and txs are not equal, return false.
      if (validTxs.length != txs.size()) {
         return false;
      }

      // Add utxo to utxoPool.
      UTXOPool utxoPool = txHandler.getUTXOPool();
      UTXO coinbaseUtxo = new UTXO(b.getCoinbase().getHash(), 0);
      Transaction.Output coinbaseOutput = b.getCoinbase().getOutput(0);
      utxoPool.addUTXO(coinbaseUtxo, coinbaseOutput);

      // Create a new BlockNode for this block.
      BlockNode newBlock = new BlockNode(b, parentBlock, utxoPool);

      // Check the height considering CUT_OFF_AGE. Return false if newBlock.height <= maxHeightBlock.height - CUT_OFF_AGE
      if (newBlock.height <= maxHeightBlock.height - CUT_OFF_AGE) {
         return false;
      }

      // Remove the transactions from the transactionPool that are in this new block.
      ArrayList<Transaction> blockTxs = b.getTransactions();
      for (int i = 0; i < blockTxs.size(); ++i) {
         Transaction tx = blockTxs.get(i);
         transactionPool.removeTransaction(tx.getHash());
      }

      /* 
       * Check the heights. If the current height is higher than maxHeightBlock's height,
       * update maxHeightBlock to this new block.
       */
      int currentHeight = parentBlock.height + 1;
      if (currentHeight > maxHeightBlock.height) {
         maxHeightBlock = newBlock;
      }

      // Add the new block to the block mapping and return true.
      blockMapping.put(new ByteArrayWrapper(b.getHash()), newBlock);
      return true;
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      transactionPool.addTransaction(tx);
   }
}
