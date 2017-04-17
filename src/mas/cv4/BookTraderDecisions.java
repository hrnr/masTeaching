package mas.cv4;

import java.util.ArrayList;

import jade.domain.FIPAAgentManagement.RefuseException;
import mas.cv4.onto.BookInfo;
import mas.cv4.onto.ChooseFrom;
import mas.cv4.onto.Offer;

public interface BookTraderDecisions {

	/**
	 * The list of books the agent should request from other agents. This method
	 * is called once per second.
	 *
	 * @return might return null or empty list
	 */
	ArrayList<BookInfo> getBooksToBuy();

	/**
	 * Based on the request from other agent, prepare a list of trade offers.
	 *
	 * @param requestedBooks
	 *            The list of books the agent wants from us, based on which we
	 *            should create the ChooseFrom.setWillSell() list. However, it
	 *            appears we can put there only a subset or even completely
	 *            different books.
	 * @return ChooseFrom object with a list of books that we are willing to
	 *         sell and a list of "offers": what we want in return
	 * @throws RefuseException
	 *             If we do not want to create offer for this request
	 */
	ChooseFrom createOffers(ArrayList<BookInfo> requestedBooks) throws RefuseException;

	/**
	 * Choose at most 1 offer we want to accept from the lists of offers we
	 * received in response to our request.
	 *
	 * @param fulfillableOffers
	 *            The lists ChooseFrom.getOffers() are filtered to only contain
	 *            the offers that we can fulfill, i.e. we have all the books and
	 *            enough money
	 * @return An offer or null if we want to reject all offers
	 */
	Offer selectFromOffers(ArrayList<ChooseFrom> fulfillableOffers);

	/**
	 * Called when our offer was refused.
	 *
	 * @param chooseFrom
	 */
	default void ourOfferRefusedCallback(ChooseFrom chooseFrom) {
	};

	/**
	 * Called after someone accepts our offer and the transaction is complete.
	 * Our money and list of books are already updated.
	 *
	 * @param receivedBooks
	 * @param paid
	 */
	default void ourOfferAcceptedCallback(ArrayList<BookInfo> receivedBooks, Offer paid) {

	}
}