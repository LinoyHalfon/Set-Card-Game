package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;
import java.util.LinkedList;



/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    protected final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated. 
     */
    protected volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The elapsed time since dealer's last action.
     */
    private long elapsedTime;

    protected int numOfPreparedPlayers = 0;

    protected Thread dealerThread;

    protected Thread[] playersThreads;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.terminate = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        
        dealerThread = Thread.currentThread();
        Thread[] playersThreads = new Thread[players.length];
        for (int i = 0; i < players.length; i++){
            playersThreads[i] = new Thread(players[i], "player"+i);
            playersThreads[i].start();
        }
        synchronized (table.gameLock){
            while (!arePlayersReady() & !terminate)
                try{
                    table.gameLock.wait();
                }catch(InterruptedException ignored){}
            }
        
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        
        if (!terminate){
            terminate();
            announceWinners();
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }
        else
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!shouldFinish() && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            LinkedList<Integer> possibleSet = table.getPossibleSet();
            while (possibleSet != null & !shouldFinish() & System.currentTimeMillis() < reshuffleTime){
                int possibleSetPlayerId = possibleSet.poll();
                if (table.isSet(possibleSet)){
                    players[possibleSetPlayerId].point();
                    removeCardsFromTable(possibleSet);
                    placeCardsOnTable();
                    updateTimerDisplay(true);
                }
                else{
                    players[possibleSetPlayerId].penalty();
                    updateTimerDisplay(false);
                }
                possibleSet = table.getPossibleSet();
            }
        }
    }

    /**
     * Called when the game should be terminated 
     */
    public void terminate() {
        // TODO implement
        for (int i = players.length-1; i >= 0; i--){
            players[i].terminate(); 
            try{
                players[i].playerThread.join();
            }catch(InterruptedException ignored){};
        }
        terminate = true;
        dealerThread.interrupt(); 
 
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    protected boolean shouldFinish() {
        return terminate || (env.util.findSets(deck, 1).size() == 0 & !table.setCanBeFound());
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(LinkedList<Integer> slots) {
        // TODO implement
        try{
            table.possibleSetsSem.acquire();
        }catch(InterruptedException ignored){}
        table.isAvailable.set(false);
        for (Player player : players){
            try{
                player.tokensSem.acquire();
            }catch(InterruptedException ignored){}
            player.clearKeyPresses();
        }
        for (int i = 0; i<slots.size(); i++){
            for (Player player : players){
                player.removeToken(slots.get(i));
            }
            table.removeCard(slots.get(i), players);
        }
        for (Player player : players){
            player.tokensSem.release();
        }
        table.possibleSetsSem.release();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        boolean isDeckEmpty = deck.isEmpty();
        Collections.shuffle(deck);
        LinkedList<Integer> slotsList = new LinkedList<Integer>();
        for (int i = 0; i<env.config.tableSize; i++)
            slotsList.add(i);
        Collections.shuffle(slotsList);
        for (int index = 0; index<(env.config.tableSize) & !isDeckEmpty; index++){
            if (table.isSlotNull(slotsList.get(index))) {
                table.placeCard(deck.get(0), slotsList.get(index));
                deck.remove(0);
                isDeckEmpty = deck.isEmpty();
            }
        }
        if (env.config.turnTimeoutMillis <= 0){
            if (!table.setCanBeFound()){
                removeAllCardsFromTable();
                if (!shouldFinish())
                    placeCardsOnTable();
                else
                    return;
            }
        }
        if (env.config.turnTimeoutMillis > 0)
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; 
        if (env.config.turnTimeoutMillis == 0)
            elapsedTime = System.currentTimeMillis();
        if (!terminate){
            synchronized (table.tableLock){
                table.isAvailable.set(true);
                table.tableLock.notifyAll();
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        if (env.config.turnTimeoutMillis > 0 & (reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis)){
            try {
                Thread.sleep(10);
            }
            catch(InterruptedException ignored){}
        }
        else{
            try {
                Thread.sleep(999);
            }
            catch(InterruptedException ignored){}
        }  
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    protected void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (env.config.turnTimeoutMillis > 0){
            if (reset)
                env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            else{
                long currTime = reshuffleTime - System.currentTimeMillis();
                env.ui.setCountdown(Math.max(0, currTime),(currTime <= env.config.turnTimeoutWarningMillis));
            }
        }
        if (env.config.turnTimeoutMillis == 0)
            env.ui.setElapsed(System.currentTimeMillis() - elapsedTime);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        try{
            table.possibleSetsSem.acquire();
        }catch(InterruptedException ignored){};
        table.isAvailable.set(false);
        for (Player player : players){
            try{
                player.tokensSem.acquire();
            }catch(InterruptedException ignored){};
            player.removeAllTokens(); 
        }
        table.removeAllTokens();
        LinkedList<Integer> slotsList = new LinkedList<Integer>();
        for (int i = 0; i<env.config.tableSize; i++)
            slotsList.add(i);
        Collections.shuffle(slotsList);
        for (int index = 0; index<env.config.tableSize; index++){
            Integer removedCard = table.removeCard(slotsList.get(index), players);
            if (removedCard != null)
                deck.add(removedCard);
            for (Player player : players){
                player.clearKeyPresses();
            }
        }
        for (Player player : players){
            player.tokensSem.release();
        }
        table.possibleSetsSem.release();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        Integer maxScore = 0;
        List<Integer> winnersId= new LinkedList<Integer>();
        for (Player player : players){ 
            if (player.score() > maxScore){
                maxScore = player.score();
                winnersId = new LinkedList<Integer>();
                winnersId.add(player.id);
            }
            else if (player.score() == maxScore)
                winnersId.add(player.id);
        }
        int[] finalWinnersId = new int[winnersId.size()];
        for (int i = 0; i<winnersId.size(); i++)
            finalWinnersId[i] = winnersId.get(i);
        env.ui.announceWinner(finalWinnersId);
    }

    public boolean arePlayersReady(){
        return numOfPreparedPlayers == players.length;
    }
    
}
