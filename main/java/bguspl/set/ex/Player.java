package bguspl.set.ex;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.LinkedList;
import bguspl.set.Env;
import java.util.concurrent.Semaphore;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

     /**
     * Player's current tokens.
     */
    private LinkedList<Integer> tokens;

    /**
     * Player's key presses.
     */
    protected final LinkedBlockingQueue<Integer> keyPresses;

    /**
     * How long should player be frozen.
     */
    private long freezeTimer;

    /**
     * Game's dealer
     */
    private Dealer dealer;

    protected Semaphore tokensSem = new Semaphore(1, true);

    protected boolean isAiReady = false;

    protected boolean setIsReady = false;

    protected Object waitingToCheckLock = new Object();

    protected LinkedList<Integer> possibleSet = new LinkedList<Integer>();


    /**
     * True if player should wait for response
     */
    protected volatile boolean shouldWait;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.tokens = new LinkedList<Integer>();
        this.freezeTimer = -1;
        this.shouldWait = false;
        this.keyPresses = new LinkedBlockingQueue<Integer>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + "starting.");
        synchronized (table.gameLock){
            if (!human) {
                if (!isAiReady & !terminate)
                    createArtificialIntelligence();
                while(!dealer.arePlayersReady() & !terminate){
                    try{
                        table.gameLock.wait();
                     }catch(InterruptedException ignored){}
                }
            }
            
            else{
                isAiReady = true;
                dealer.numOfPreparedPlayers++;
                if (dealer.arePlayersReady())
                    table.gameLock.notifyAll();
                else{
                    while(!dealer.arePlayersReady() & !terminate){
                        try{
                            table.gameLock.wait();
                             }catch(InterruptedException ignored){}
                    }
                }
            }
        }

        while (!terminate) {
            // TODO implement main player loop
            while (freezeTimer > 0 & !terminate){
                try {
                    Thread.sleep(Math.min(1000,freezeTimer));
                    freezeTimer = freezeTimer - Math.min(1000,freezeTimer);
                    env.ui.setFreeze(id, Math.max(0,freezeTimer));
                }catch(InterruptedException ignored){}
            }
            if (!table.isAvailable.get() & !terminate){
                synchronized (table.tableLock){   
                    while (!table.isAvailable.get() & !terminate)
                        try{
                            table.tableLock.wait();
                        }catch(InterruptedException ignored){}
                    }
                }
            
            if (!terminate)
                actionExecuter();
        }         
        if (!human){
            try{
                aiThread.join();
            }catch(InterruptedException ignored){};
        }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }
    


    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
     private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
            // TODO implement player key press simulator
                if (!isAiReady){
                    synchronized (table.gameLock){
                        while (!isAiReady & !terminate){
                            if (keyPresses.size() == env.config.featureSize){
                                isAiReady = true;
                                dealer.numOfPreparedPlayers++;
                                if (dealer.arePlayersReady())
                                    table.gameLock.notifyAll();
                            }
                            else{
                                int keyPressed = (int) (Math.random() * (env.config.tableSize));
                                try{
                                    keyPresses.put(keyPressed);
                                }catch(InterruptedException ignored){}
                            }
                        }
                    }
                }
                else{
                    int keyPressed = (int) (Math.random() * (env.config.tableSize));
                    try{
                        keyPresses.put(keyPressed);
                    }catch(InterruptedException ex){
                        if (terminate)
                            break;
                    }
                }
            }              
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        if (!human)
            aiThread.interrupt();
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (freezeTimer <= 0 & !table.isSlotNull(slot)){
            if (tokens.size() < env.config.featureSize | (tokens.size() == env.config.featureSize & tokens.contains(slot))){
                keyPresses.offer(slot);
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        synchronized (waitingToCheckLock){
            score++;
            freezeTimer = env.config.pointFreezeMillis;
            env.ui.setScore(id, score);
            env.ui.setFreeze(id, freezeTimer);
            clearKeyPresses();
            shouldWait = false;
            setIsReady = false;
            this.waitingToCheckLock.notifyAll();
        }
        

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {
        // TODO implement
        synchronized (waitingToCheckLock){
            freezeTimer = env.config.penaltyFreezeMillis;
            env.ui.setFreeze(id, freezeTimer);
            clearKeyPresses();
            shouldWait = false;
            setIsReady = false;
            this.waitingToCheckLock.notifyAll();
        }
    }

    public int score() {
        return score;
    }

    public int getId() {
        return id;
    }

    public LinkedList<Integer> getTokens() {
        return tokens;
    }

    public void removeToken(Integer slot){
        synchronized (this.waitingToCheckLock){
            if (tokens.remove(slot)){
                table.removeToken(this.id, slot);
                shouldWait = false;
                setIsReady = false;
                this.waitingToCheckLock.notifyAll();
            }
        }
    }

    public void removeAllTokens(){
        synchronized (this.waitingToCheckLock){
            tokens.clear();
            shouldWait = false;
            setIsReady = false;
            this.waitingToCheckLock.notifyAll();
        }
    }

    public void placeToken(Integer slot){
        if (!table.isSlotNull(slot)){
            if (tokens.add(slot)){
                table.placeToken(this.id, slot);
            }
        }
    }

    public void clearKeyPresses(){
        keyPresses.clear();
    }

    public int getNextAction(){
        try {
            Integer slot = keyPresses.take();
            return slot;
        }catch(InterruptedException ex){return -1;}
    }


    public void actionExecuter(){
        //execute action
        possibleSet = new LinkedList<Integer>();
        if (table.isAvailable.get()) { 
            setIsReady = false;
            Integer slot = getNextAction();
            if (!terminate & slot != -1){
                try{
                    tokensSem.acquire();
                }catch(InterruptedException ignored){}
                //case 1 - remove token
                if (tokens.contains(slot))
                    removeToken(slot);
                //case 2 - maybe place token
                else if (tokens.size() != env.config.featureSize){
                    placeToken(slot);
                    if (tokens.size() == env.config.featureSize){
                        possibleSet.addFirst(id);
                        possibleSet.addAll(tokens);
                        setIsReady = true;
                    }
                }
                tokensSem.release();
            }        
        }
        //check if set should be added to possible sets
        if (setIsReady & !terminate){ 
            try{
                table.possibleSetsSem.acquire();
            }catch(InterruptedException ignored){}
            boolean semReleased = false;
            synchronized (waitingToCheckLock) {
                if (tokens.size() == env.config.featureSize & setIsReady & !shouldWait & !terminate){
                    table.addPossibleSet(possibleSet);
                    shouldWait = true;
                }
                while (shouldWait & !terminate){
                    if (!semReleased){
                        table.possibleSetsSem.release();
                        semReleased = true;
                        dealer.dealerThread.interrupt();
                    }
                    try{
                        waitingToCheckLock.wait();
                     }catch(InterruptedException ignored){}
                }
                if (!semReleased){
                    table.possibleSetsSem.release();
                    semReleased = true;
                }
            }
        }
    }
}
