package mas.cv4;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.UngroundedException;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetInitiator;
import jade.proto.SSContractNetResponder;
import jade.proto.SSResponderDispatcher;
import mas.cv4.onto.AgentInfo;
import mas.cv4.onto.BookInfo;
import mas.cv4.onto.BookOntology;
import mas.cv4.onto.ChooseFrom;
import mas.cv4.onto.Chosen;
import mas.cv4.onto.GetMyInfo;
import mas.cv4.onto.Goal;
import mas.cv4.onto.MakeTransaction;
import mas.cv4.onto.Offer;
import mas.cv4.onto.SellMeBooks;
import mas.cv4.onto.StartTrading;

public abstract class BookTraderBase extends Agent implements BookTraderDecisions {

	protected Codec codec = new SLCodec();
	protected Ontology onto = BookOntology.getInstance();
	protected ArrayList<BookInfo> myBooks;
	protected ArrayList<Goal> myGoal;
	protected double myMoney;
	protected AgentInfo myAgentInfo;
	protected Random rnd = new Random();

	public BookTraderBase() {
		super();
	}

	@Override
	protected void setup() {
		super.setup();

		// register the codec and the ontology with the content manager
		this.getContentManager().registerLanguage(codec);
		this.getContentManager().registerOntology(onto);

		// book-trader service description
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-trader");
		sd.setName("book-trader");

		// description of this agent and the services it provides
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(this.getAID());
		dfd.addServices(sd);

		// register to DF
		try {
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}

		// add behavior which waits for the StartTrading message
		addBehaviour(new StartTradingBehaviour(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));
	}

	@Override
	protected void takeDown() {
		super.takeDown();
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns book info about the book this agent has (including book ID).
	 * Returns null if the agent does not have a book with the same name.
	 *
	 * @param b
	 * @return
	 */
	protected BookInfo getOwnedInstanceOfBook(BookInfo b) {
		for (BookInfo myBook : myBooks) {
			if (myBook.getBookName().equals(b.getBookName())) {
				return myBook;
			}
		}
		return null;
	}

	int getOwnedInstancesCount(BookInfo b) {
		String bookName = b.getBookName();
		return getOwnedInstancesCount(bookName);
	}

	private int getOwnedInstancesCount(String bookName) {
		int count = 0;
		for (BookInfo myBook : myBooks) {
			if (myBook.getBookName().equals(bookName)) {
				count++;
			}
		}
		return count;
	}

	boolean hasMoreThanOneInstance(BookInfo b) {
		return getOwnedInstancesCount(b) > 1;
	}

	boolean isGoalBook(BookInfo b) {
		String bookname = b.getBookName();
		return isGoalBook(bookname);
	}

	private boolean isGoalBook(String bookname) {
		for (Goal goal : myGoal) {
			if (goal.getBook().getBookName().equals(bookname)) {
				return true;
			}
		}
		return false;
	}

	boolean isMissingGoalBook(BookInfo b) {
		return isGoalBook(b) && getOwnedInstanceOfBook(b) == null;
	}

	double getGoalBookPrice(BookInfo b) {
		String bookName = b.getBookName();
		for (Goal goal : myGoal) {
			if (goal.getBook().getBookName().equals(bookName)) {
				return goal.getValue();
			}
		}
		throw new IllegalArgumentException("The given book is not agents goal book.");
	}

	ArrayList<BookInfo> getMissingGoalBooks() {
		ArrayList<BookInfo> missingBooks = new ArrayList<BookInfo>();
		for (Goal goal : myGoal) {
			if (getOwnedInstanceOfBook(goal.getBook()) == null) {
				missingBooks.add(goal.getBook());
			}
		}
		return missingBooks;
	}

	ArrayList<String> getUnnecessaryBooks() {
		ArrayList<String> unnecBooks = new ArrayList<>();
		for (String book : Constants.getBooknames()) {
			int count = getOwnedInstancesCount(book);
			if (isGoalBook(book)) {
				count--;
			}
			if (count >= 1) {
				unnecBooks.add(book);
			}
		}
		return unnecBooks;
	}

	void log(String s) {
		System.out.println(getName() + ": " + s);
	}

	// waits for the StartTrading message and adds the trading behavior
	class StartTradingBehaviour extends AchieveREResponder {

		public StartTradingBehaviour(Agent a, MessageTemplate mt) {
			super(a, mt);
		}

		@Override
		protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {

			try {
				ContentElement ce = getContentManager().extractContent(request);

				if (!(ce instanceof Action)) {
					throw new NotUnderstoodException("");
				}
				Action a = (Action) ce;

				// we got the request to start trading
				if (a.getAction() instanceof StartTrading) {

					// find out what our goals are
					ACLMessage getMyInfo = new ACLMessage(ACLMessage.REQUEST);
					getMyInfo.setLanguage(codec.getName());
					getMyInfo.setOntology(onto.getName());

					ServiceDescription sd = new ServiceDescription();
					sd.setType("environment");
					DFAgentDescription dfd = new DFAgentDescription();
					dfd.addServices(sd);

					DFAgentDescription[] envs = DFService.search(myAgent, dfd);

					getMyInfo.addReceiver(envs[0].getName());
					getContentManager().fillContent(getMyInfo, new Action(envs[0].getName(), new GetMyInfo()));

					ACLMessage myInfo = FIPAService.doFipaRequestClient(myAgent, getMyInfo);

					Result res = (Result) getContentManager().extractContent(myInfo);

					AgentInfo ai = (AgentInfo) res.getValue();

					myBooks = ai.getBooks();
					myGoal = ai.getGoals();
					myMoney = ai.getMoney();

					// add a behavior which tries to buy a book every two
					// seconds
					addBehaviour(new TradingBehaviour(myAgent, 1000));

					// add a behavior which sells book to other agents
					addBehaviour(new SellBook(myAgent, MessageTemplate.MatchPerformative(ACLMessage.CFP)));

					// reply that we are able to start trading (the message is
					// ignored by the environment)
					ACLMessage reply = request.createReply();
					reply.setPerformative(ACLMessage.INFORM);
					return reply;
				}

				throw new NotUnderstoodException("");

			} catch (Codec.CodecException e) {
				e.printStackTrace();
			} catch (OntologyException e) {
				e.printStackTrace();
			} catch (FIPAException e) {
				e.printStackTrace();
			}

			return super.handleRequest(request);
		}

		// this behavior trades with books
		class TradingBehaviour extends TickerBehaviour {

			public TradingBehaviour(Agent a, long period) {
				super(a, period);
			}

			@Override
			protected void onTick() {

				try {

					// find other seller and prepare a CFP
					ServiceDescription sd = new ServiceDescription();
					sd.setType("book-trader");
					DFAgentDescription dfd = new DFAgentDescription();
					dfd.addServices(sd);

					DFAgentDescription[] traders = DFService.search(myAgent, dfd);

					ACLMessage buyBook = new ACLMessage(ACLMessage.CFP);
					buyBook.setLanguage(codec.getName());
					buyBook.setOntology(onto.getName());
					buyBook.setReplyByDate(new Date(System.currentTimeMillis() + 5000));

					for (DFAgentDescription dfad : traders) {
						if (dfad.getName().equals(myAgent.getAID()))
							continue;
						buyBook.addReceiver(dfad.getName());
					}

					ArrayList<BookInfo> bis = getBooksToBuy();
					if (bis == null || bis.isEmpty())
						return;
					SellMeBooks smb = new SellMeBooks();
					smb.setBooks(bis);

					getContentManager().fillContent(buyBook, new Action(myAgent.getAID(), smb));
					addBehaviour(new ObtainBook(myAgent, buyBook));
				} catch (Codec.CodecException e) {
					e.printStackTrace();
				} catch (OntologyException e) {
					e.printStackTrace();
				} catch (FIPAException e) {
					e.printStackTrace();
				}

			}

		}

		// this behavior takes care of the buying of the book itself
		class ObtainBook extends ContractNetInitiator {

			public ObtainBook(Agent a, ACLMessage cfp) {
				super(a, cfp);
			}

			Chosen c; // we need to remember what offer we have chosen
			ArrayList<BookInfo> shouldReceive; // we also remember what the
												// seller offered to us

			// the seller informs us it processed the order, we need to send the
			// payment
			@Override
			protected void handleInform(ACLMessage inform) {
				try {

					// create the transaction info and send it to the
					// environment
					MakeTransaction mt = new MakeTransaction();

					mt.setSenderName(myAgent.getName());
					mt.setReceiverName(inform.getSender().getName());
					mt.setTradeConversationID(inform.getConversationId());

					if (c.getOffer().getBooks() == null)
						c.getOffer().setBooks(new ArrayList<BookInfo>());

					mt.setSendingBooks(c.getOffer().getBooks());
					mt.setSendingMoney(c.getOffer().getMoney());

					if (shouldReceive == null)
						shouldReceive = new ArrayList<BookInfo>();

					mt.setReceivingBooks(shouldReceive);
					mt.setReceivingMoney(0.0);

					ServiceDescription sd = new ServiceDescription();
					sd.setType("environment");
					DFAgentDescription dfd = new DFAgentDescription();
					dfd.addServices(sd);

					DFAgentDescription[] envs = DFService.search(myAgent, dfd);

					ACLMessage transReq = new ACLMessage(ACLMessage.REQUEST);
					transReq.addReceiver(envs[0].getName());
					transReq.setLanguage(codec.getName());
					transReq.setOntology(onto.getName());
					transReq.setReplyByDate(new Date(System.currentTimeMillis() + 5000));

					getContentManager().fillContent(transReq, new Action(envs[0].getName(), mt));
					addBehaviour(new SendBook(myAgent, transReq, shouldReceive, c));

				} catch (UngroundedException e) {
					e.printStackTrace();
				} catch (OntologyException e) {
					e.printStackTrace();
				} catch (Codec.CodecException e) {
					e.printStackTrace();
				} catch (FIPAException e) {
					e.printStackTrace();
				}

			}

			// process the offers from the sellers
			@Override
			protected void handleAllResponses(Vector responses, Vector acceptances) {

				Iterator it = responses.iterator();

				// we need to accept only one offer, otherwise we create two
				// transactions with the same ID
				boolean accepted = false;
				ArrayList<ACLMessage> positiveResponses = new ArrayList<ACLMessage>();
				ArrayList<ChooseFrom> offersByAgents = new ArrayList<ChooseFrom>();

				while (it.hasNext()) {
					ACLMessage response = (ACLMessage) it.next();

					ContentElement ce = null;

					if (response.getPerformative() == ACLMessage.REFUSE) {
						continue;
					}

					try {
						ce = getContentManager().extractContent(response);
					} catch (Codec.CodecException | OntologyException e) {
//						e.printStackTrace();
						continue;
					}
					ChooseFrom cf = (ChooseFrom) ce;
					positiveResponses.add(response);

					ArrayList<Offer> offers = cf.getOffers();
					offersByAgents.add(cf);
					ArrayList<Offer> fulfillableOffers = new ArrayList<Offer>();
					// find out which offers we can fulfill (we have all
					// requested books and enough money)
					for (Offer o : offers) {
						if (o.getMoney() > myMoney)
							continue;

						boolean foundAll = true;
						if (o.getBooks() != null)
							for (BookInfo bi : o.getBooks()) {
								String bn = bi.getBookName();
								boolean found = false;
								for (int j = 0; j < myBooks.size(); j++) {
									if (myBooks.get(j).getBookName().equals(bn)) {
										found = true;
										bi.setBookID(myBooks.get(j).getBookID());
										break;
									}
								}
								if (!found) {
									foundAll = false;
									break;
								}
							}

						if (foundAll) {
							fulfillableOffers.add(o);
						}
					}
					cf.setOffers(fulfillableOffers);
				}
				Offer best = selectFromOffers(offersByAgents);
				for (int i = 0; i < positiveResponses.size(); i++) {
					ACLMessage response = positiveResponses.get(i);
					ChooseFrom cf = offersByAgents.get(i);
					ArrayList<Offer> offers = cf.getOffers();
					if (best == null || !offers.contains(best)) {
						// Negative answer:
						ACLMessage acc = response.createReply();
						acc.setPerformative(ACLMessage.REJECT_PROPOSAL);
						acceptances.add(acc);
					} else {
						// Positive answer:
						ACLMessage acc = response.createReply();
						acc.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
						Chosen ch = new Chosen();
						ch.setOffer(best);
						c = ch;
						shouldReceive = cf.getWillSell();
						try {
							getContentManager().fillContent(acc, ch);
						} catch (CodecException | OntologyException e) {
							e.printStackTrace();
							continue;
						}
						acceptances.add(acc);
					}
				}
			}
		}

		// this behavior processes the selling of books
		class SellBook extends SSResponderDispatcher {

			public SellBook(Agent a, MessageTemplate tpl) {
				super(a, tpl);
			}

			@Override
			protected Behaviour createResponder(ACLMessage initiationMsg) {
				return new SellBookResponder(myAgent, initiationMsg);
			}
		}

		class SellBookResponder extends SSContractNetResponder {

			public SellBookResponder(Agent a, ACLMessage cfp) {
				super(a, cfp);
			}

			@Override
			protected ACLMessage handleCfp(ACLMessage cfp)
					throws RefuseException, FailureException, NotUnderstoodException {

				try {
					Action ac = (Action) getContentManager().extractContent(cfp);

					SellMeBooks smb = (SellMeBooks) ac.getAction();
					ArrayList<BookInfo> books = smb.getBooks();

					ChooseFrom cf = createOffers(books);

					// send the offers
					ACLMessage reply = cfp.createReply();
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setReplyByDate(new Date(System.currentTimeMillis() + 5000));
					getContentManager().fillContent(reply, cf);

					return reply;
				} catch (UngroundedException e) {
					e.printStackTrace();
				} catch (Codec.CodecException e) {
					e.printStackTrace();
				} catch (OntologyException e) {
					e.printStackTrace();
				}

				throw new FailureException("");
			}

			// the buyer decided to accept an offer
			@Override
			protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept)
					throws FailureException {

				try {
					ChooseFrom cf = (ChooseFrom) getContentManager().extractContent(propose);

					// prepare the transaction info and send it to the
					// environment
					MakeTransaction mt = new MakeTransaction();

					mt.setSenderName(myAgent.getName());
					mt.setReceiverName(cfp.getSender().getName());
					mt.setTradeConversationID(cfp.getConversationId());

					if (cf.getWillSell() == null) {
						cf.setWillSell(new ArrayList<BookInfo>());
					}

					mt.setSendingBooks(cf.getWillSell());
					mt.setSendingMoney(0.0);

					Chosen c = (Chosen) getContentManager().extractContent(accept);

					if (c.getOffer().getBooks() == null) {
						c.getOffer().setBooks(new ArrayList<BookInfo>());
					}

					mt.setReceivingBooks(c.getOffer().getBooks());
					mt.setReceivingMoney(c.getOffer().getMoney());

					ServiceDescription sd = new ServiceDescription();
					sd.setType("environment");
					DFAgentDescription dfd = new DFAgentDescription();
					dfd.addServices(sd);

					DFAgentDescription[] envs = DFService.search(myAgent, dfd);

					ACLMessage transReq = new ACLMessage(ACLMessage.REQUEST);
					transReq.addReceiver(envs[0].getName());
					transReq.setLanguage(codec.getName());
					transReq.setOntology(onto.getName());
					transReq.setReplyByDate(new Date(System.currentTimeMillis() + 5000));

					getContentManager().fillContent(transReq, new Action(envs[0].getName(), mt));

					addBehaviour(new SendBook(myAgent, transReq));

					ACLMessage reply = accept.createReply();
					reply.setPerformative(ACLMessage.INFORM);
					return reply;

				} catch (UngroundedException e) {
					e.printStackTrace();
				} catch (OntologyException e) {
					e.printStackTrace();
				} catch (Codec.CodecException e) {
					e.printStackTrace();
				} catch (FIPAException e) {
					e.printStackTrace();
				}

				throw new FailureException("");
			}

			@Override
			protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
				super.handleRejectProposal(cfp, propose, reject);
				ChooseFrom cf;
				try {
					cf = (ChooseFrom) getContentManager().extractContent(propose);

				} catch (CodecException | OntologyException e) {
					e.printStackTrace();
					return;
				}
				ourOfferRefusedCallback(cf);
			}
		}

		// after the transaction is complete (the environment returned an
		// INFORM), we update our information
		class SendBook extends AchieveREInitiator {

			private Chosen chosen;
			private ArrayList<BookInfo> shouldReceive;

			public SendBook(Agent a, ACLMessage msg) {
				super(a, msg);
			}

			public SendBook(Agent a, ACLMessage msg, ArrayList<BookInfo> shouldReceive, Chosen chosen) {
				super(a, msg);
				this.shouldReceive = shouldReceive;
				this.chosen = chosen;
			}

			@Override
			protected void handleInform(ACLMessage inform) {

				try {
					ACLMessage getMyInfo = new ACLMessage(ACLMessage.REQUEST);
					getMyInfo.setLanguage(codec.getName());
					getMyInfo.setOntology(onto.getName());

					ServiceDescription sd = new ServiceDescription();
					sd.setType("environment");
					DFAgentDescription dfd = new DFAgentDescription();
					dfd.addServices(sd);

					DFAgentDescription[] envs = DFService.search(myAgent, dfd);

					getMyInfo.addReceiver(envs[0].getName());
					getContentManager().fillContent(getMyInfo, new Action(envs[0].getName(), new GetMyInfo()));

					ACLMessage myInfo = FIPAService.doFipaRequestClient(myAgent, getMyInfo);

					Result res = (Result) getContentManager().extractContent(myInfo);

					AgentInfo ai = (AgentInfo) res.getValue();

					myBooks = ai.getBooks();
					myGoal = ai.getGoals();
					myMoney = ai.getMoney();
					myAgentInfo = ai;
					// Callback invoked only when the transaction was based on
					// our offer.
					if (shouldReceive != null && chosen != null) {
						ourOfferAcceptedCallback(shouldReceive, chosen.getOffer());
					}
				} catch (OntologyException e) {
					e.printStackTrace();
				} catch (FIPAException e) {
					e.printStackTrace();
				} catch (Codec.CodecException e) {
					e.printStackTrace();
				}

			}
		}
	}
}