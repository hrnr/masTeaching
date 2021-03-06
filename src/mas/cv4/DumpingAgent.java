package mas.cv4;

import java.util.ArrayList;
import java.util.stream.Collectors;

import jade.domain.FIPAAgentManagement.RefuseException;
import mas.cv4.onto.BookInfo;
import mas.cv4.onto.ChooseFrom;
import mas.cv4.onto.Offer;

/**
 * <p>
 * Tries to sell out all non-goal books for low prices, because it is a big loss
 * if we don't sell the books at all.
 * <p>
 * Tries to buy goal books a bit cheaper than our goal price. Does not buy books
 * that are not our goal books.
 *
 * @author Tomas Prochazka
 *
 */
public class DumpingAgent extends BookTraderBase {

	// Discount from the standard book price
	public double discount = 20 + 4 + Double.MIN_VALUE;
	// The minimum gain when selling or buying our goal book
	private static final int MINIMAL_PROFIT = 1;
	// how much we will dump the lowest bid
	private static final double DUMP_VALUE = 1 + Double.MIN_VALUE;

	@Override
	public ArrayList<BookInfo> getBooksToBuy() {
		ArrayList<BookInfo> missingGoalBooks = new ArrayList<>(
				Constants.getBooknames().stream().map(x -> {BookInfo bi = new BookInfo(); bi.setBookName(x); return bi;})
				.collect(Collectors.toList()));
		log("I buy  books: " + missingGoalBooks.toString());
		log("I sell books: " + getUnnecessaryBooks());
		return missingGoalBooks;
	}

	@Override
	public ChooseFrom createOffers(ArrayList<BookInfo> requestedBooks) throws RefuseException {
		double price = 0;
		ArrayList<BookInfo> sellBooks = new ArrayList<BookInfo>();
		for (BookInfo requestedBook : requestedBooks) {
			BookInfo ownedInstance = getOwnedInstanceOfBook(requestedBook);
			if (ownedInstance != null) {
				sellBooks.add(ownedInstance);
				price += evaluateSellingPrice(ownedInstance);
			}
		}
		if (sellBooks.isEmpty())
			throw new RefuseException("I do not have any of the requested books.");
		Offer o = new Offer();
		o.setMoney(price);
		ChooseFrom chooseFrom = new ChooseFrom();
		ArrayList<Offer> offers = new ArrayList<>();
		offers.add(o);
		chooseFrom.setOffers(offers);
		chooseFrom.setWillSell(sellBooks);
		// log("Placing offer.");
		return chooseFrom;
	}

	private double evaluateSellingPrice(BookInfo book) {
		if (isGoalBook(book) && !hasMoreThanOneInstance(book)) {
			return getGoalBookPrice(book) + MINIMAL_PROFIT;
		}
		return Constants.getPrice(book.getBookName()) - discount;
	}

	// choose the offer with the highest profit
	@Override
	public Offer selectFromOffers(ArrayList<ChooseFrom> fulfillableOffers) {
		Offer bestOffer = null;
		double highestProfit = 0;
		for (ChooseFrom chooseFrom : fulfillableOffers) {
			double boughtBooksPrice = evaluateBuyingPrice(chooseFrom.getWillSell());
			double ourPrice = evaluateSellingPrice(chooseFrom.getWillSell());
			for (Offer offer : chooseFrom.getOffers()) {
				double priceForBooks = 0;
				if (offer.getBooks() != null) {
					priceForBooks = evaluateSellingPrice(offer.getBooks());
				}
				double expense = priceForBooks + offer.getMoney();
				double profit = boughtBooksPrice - expense;
				if (profit > highestProfit) {
					bestOffer = offer;
					highestProfit = profit;
				}
				// adjust our discount in case another agent is dumping more
				// than us
				if (ourPrice > expense) {
					double newDiscount = (ourPrice - expense) / chooseFrom.getWillSell().size();
					newDiscount += DUMP_VALUE;
					discount = Math.max(discount, newDiscount);
					log("NEW DISCOUNT: " + discount);
				}
			}
		}
		return bestOffer;
	}

	private double evaluateBuyingPrice(ArrayList<BookInfo> books) {
		return books.stream().mapToDouble(b -> evaluateBuyingPrice(b)).sum();
	}

	private double evaluateSellingPrice(ArrayList<BookInfo> books) {
		return books.stream().mapToDouble(b -> evaluateSellingPrice(b)).sum();
	}

	private double evaluateBuyingPrice(BookInfo b) {
		if (isMissingGoalBook(b))
			return getGoalBookPrice(b) + MINIMAL_PROFIT;
		return 0; // we don't care about books we don't need
	}

	@Override
	public void ourOfferAcceptedCallback(ArrayList<BookInfo> receivedBooks, Offer paid) {
		super.ourOfferAcceptedCallback(receivedBooks, paid);
	}

	@Override
	public void ourOfferRefusedCallback(ChooseFrom chooseFrom) {
		super.ourOfferRefusedCallback(chooseFrom);
		// log("My offer refused.");
	}
}
