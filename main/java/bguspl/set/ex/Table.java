package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;



/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * players possible sets that needs to be checked
     */
    protected final LinkedBlockingQueue<LinkedList<Integer>> possibleSets = new LinkedBlockingQueue<LinkedList<Integer>>();


    protected Object gameLock = new Object();

    protected Semaphore possibleSetsSem = new Semaphore(1, true);

    protected AtomicBoolean isAvailable = new AtomicBoolean(false);

    protected Object tableLock = new Object();

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    public boolean setCanBeFound() {
        List<Integer> cardsOnTable = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        List<int[]> foundSet = env.util.findSets(cardsOnTable, 1);
        return !foundSet.isEmpty();
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public boolean placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
        return true;
    }


    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public Integer removeCard(int slot, Player[] players) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement
        Integer removedCard = slotToCard[slot];
        if (removedCard != null){
            cardToSlot[removedCard] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
            Iterator<LinkedList<Integer>> iter = possibleSets.iterator();
            LinkedList<Integer> possibleSet;
            while (iter.hasNext()){
                possibleSet = iter.next();
                if (possibleSet.getLast() != -1){
                    Integer possibleSetPlayerId = possibleSet.getFirst();
                    possibleSet.removeFirst();
                    if (possibleSet.contains(slot)){
                        possibleSet.addLast(-1);
                        possibleSet.addFirst(possibleSetPlayerId);
                        Player currPlayer = players[possibleSetPlayerId];  
                        synchronized (currPlayer.waitingToCheckLock){                            
                                currPlayer.shouldWait = false;
                                currPlayer.setIsReady = false;
                                currPlayer.waitingToCheckLock.notifyAll();
                        }
                    }
                    else
                        possibleSet.addFirst(possibleSetPlayerId);
                }
            }    
        }
        return removedCard;
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement
        env.ui.removeToken(player, slot);
        return true;
    }

    public void removeAllTokens() {
        // TODO implement
        env.ui.removeTokens();
    }

    public LinkedList<Integer> getPossibleSet(){
        while (!possibleSets.isEmpty()){
            try{
                LinkedList<Integer> currSetToCheck = possibleSets.take();
                if (currSetToCheck.getLast() != -1)
                    return currSetToCheck;
            }catch (InterruptedException ignored){}
        }
        return null;
    }

    public boolean isSet(LinkedList<Integer> slots) {
        int[] possibleSet = new int[env.config.featureSize];
        for (int index = 0; index<slots.size(); index++){
            int cardId = slotToCard[slots.get(index)];
            possibleSet[index] = cardId;
        }
        return env.util.testSet(possibleSet);
    }

    public void addPossibleSet(LinkedList<Integer> possibleSet){
        try{
            possibleSets.put(possibleSet);
        }catch (InterruptedException ignored){}
    }

    public boolean isSlotNull(int slot){
        return slotToCard[slot] == null;
    }
}
