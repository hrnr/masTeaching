package mas.cv4;

import java.util.ArrayList;

import jade.domain.FIPAAgentManagement.RefuseException;
import mas.cv4.onto.BookInfo;
import mas.cv4.onto.ChooseFrom;
import mas.cv4.onto.Offer;

/**
 * Created by Martin Pilat on 16.4.14.
 *
 * A simple (testing) version of the trading agent. The agent does not trade in
 * any reasonable way, it only ensures it does not sell bosks it does not own
 * (but it can still happed from time to time if two agents asks for the same
 * book at the same time).
 *
 */
public class RandomBookTrader extends BookTraderBase {

	@Override
	public Offer selectFromOffers(ArrayList<ChooseFrom> fulfillableOffers) {
		for (ChooseFrom chooseFrom : fulfillableOffers) {
			ArrayList<Offer> offers = chooseFrom.getOffers();
			if (!offers.isEmpty())
				return offers.get(rnd.nextInt(offers.size()));
		}
		return null;
	}

	@Override
	public ChooseFrom createOffers(ArrayList<BookInfo> requestedBooks) throws RefuseException {
		ArrayList<BookInfo> sellBooks = new ArrayList<BookInfo>();
		for (BookInfo requestedBook : requestedBooks) {
			BookInfo myInstance = getOwnedInstanceOfBook(requestedBook);
			// The sample agent creates offers only if it has ALL the books that
			// are requested.
			if (myInstance == null)
				throw new RefuseException("I do not have all the requested books.");
			sellBooks.add(myInstance);
		}

		// create two offers
		Offer o1 = new Offer();
		o1.setMoney(100);

		ArrayList<BookInfo> bis = new ArrayList<BookInfo>();
		bis.add(myGoal.get(rnd.nextInt(myGoal.size())).getBook());

		Offer o2 = new Offer();
		o2.setBooks(bis);
		o2.setMoney(20);

		ArrayList<Offer> offers = new ArrayList<Offer>();
		offers.add(o1);
		offers.add(o2);

		ChooseFrom cf = new ChooseFrom();

		cf.setWillSell(sellBooks);
		cf.setOffers(offers);
		return cf;
	}

	@Override
	public ArrayList<BookInfo> getBooksToBuy() {
		ArrayList<BookInfo> bis = new ArrayList<BookInfo>();

		// choose a book from goals to buy
		BookInfo bi = new BookInfo();
		bi.setBookName(myGoal.get(rnd.nextInt(myGoal.size())).getBook().getBookName());
		bis.add(bi);
		return bis;
	}

}
