/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) Thomas Niessen. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 ******************************************************************************/
package chesspresso.game.view;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.position.InvalidFenException;
import chesspresso.position.NAG;

/**
 * @author Thomas Niessen
 */
public class ActionFactory {

	private ActionFactory() {
	}

	public static Action getNagAction(Game game, String nag) {
		return new NagAction(game, nag);
	}

	public static Action getRemoveEvaluationAction(Game game) {
		return new RemoveEvaluationAction(game);
	}

	public static Action getRemovePunctuationAction(Game game) {
		return new RemovePunctuationAction(game);
	}

	public static Action getCommentBeforeAction(Game game, GameTextViewer textViewer, Component parent) {
		return new CommentBeforeAction(game, textViewer, parent);
	}

	public static Action getCommentAfterAction(Game game, GameTextViewer textViewer, Component parent) {
		return new CommentAfterAction(game, textViewer, parent);
	}

	public static Action getResultAction(Game game, String pgnResult) {
		return new ResultAction(game, pgnResult);
	}

	public static Action getEventAction(Game game, Component parent) {
		return new EventAction(game, parent);
	}

	public static Action getSiteAction(Game game, Component parent) {
		return new SiteAction(game, parent);
	}

	public static Action getDateAction(Game game, Component parent) {
		return new DateAction(game, parent);
	}

	public static Action getRoundAction(Game game, Component parent) {
		return new RoundAction(game, parent);
	}

	public static Action getWhiteAction(Game game, Component parent) {
		return new WhiteAction(game, parent);
	}

	public static Action getBlackAction(Game game, Component parent) {
		return new BlackAction(game, parent);
	}

	public static Action getEcoAction(Game game, Component parent) {
		return new EcoAction(game, parent);
	}

	public static Action getFenAction(Game game, Component parent) {
		return new FenAction(game, parent);
	}

	public static Action getEventDateAction(Game game, Component parent) {
		return new EventDateAction(game, parent);
	}

	public static Action getWhitesEloAction(Game game, Component parent) {
		return new WhitesEloAction(game, parent);
	}

	public static Action getBlacksEloAction(Game game, Component parent) {
		return new BlacksEloAction(game, parent);
	}

	public static Action getPromoteVariationAction(Game game, Component parent) {
		return new PromoteVariationAction(game, parent);
	}

	public static Action getNullMoveAction(Game game, Component parent) {
		return new NullMoveAction(game, parent);
	}

	public static Action getDeleteRemainingMovesAction(Game game, GameTextViewer textViewer) {
		return new DeleteRemainingMovesAction(game, textViewer);
	}

	public static Action getDeleteThisVariationAction(Game game, GameTextViewer textViewer) {
		return new DeleteThisVariationAction(game, textViewer);
	}

	public static Action getStripAllCommentsAction(Game game, GameTextViewer textViewer) {
		return new StripAllCommentsAction(game, textViewer);
	}

	public static Action getStripAllVariationsAction(Game game, GameTextViewer textViewer) {
		return new StripAllVariationsAction(game, textViewer);
	}

	public static Action getStripAllNagsAction(Game game, GameTextViewer textViewer) {
		return new StripAllNagsAction(game, textViewer);
	}

	public static Action getStripAllAction(Game game, GameTextViewer textViewer) {
		return new StripAllAction(game, textViewer);
	}

	@SuppressWarnings("serial")
	private static class NagAction extends AbstractAction {

		private final Game game;

		private NagAction(Game game, String nag) {
			super(nag);
			this.game = game;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			short nag = NAG.ofString(e.getActionCommand());
			int curNode = game.getCurNode();
			if (NAG.isEvaluation(nag)) {
				game.removeEvaluationNags();
			}
			if (NAG.isPunctuationMark(nag)) {
				game.removePunctuationNags();
			}
			game.addNag(nag);
			game.gotoNode(curNode);
		}
	}

	@SuppressWarnings("serial")
	private static class RemoveEvaluationAction extends AbstractAction {

		private final Game game;

		private RemoveEvaluationAction(Game game) {
			this.game = game;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			int curNode = game.getCurNode();
			game.removeEvaluationNags();
			game.gotoNode(curNode);
		}

	}

	@SuppressWarnings("serial")
	private static class RemovePunctuationAction extends AbstractAction {

		private final Game game;

		private RemovePunctuationAction(Game game) {
			this.game = game;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			int curNode = game.getCurNode();
			game.removePunctuationNags();
			game.gotoNode(curNode);
		}
	}

	@SuppressWarnings("serial")
	private static class CommentBeforeAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;
		private final Component parent;

		private CommentBeforeAction(Game game, GameTextViewer textViewer, Component parent) {
			super("Comment before move");
			this.game = game;
			this.textViewer = textViewer;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			String currentComment = game.getPreMoveComment();
			String move = game.getLastMoveAsSanWithNumber();
			String message;
			if (move == null || move.isEmpty()) {
				message = "Your comment:";
			} else {
				message = "Your comment before " + move + ":";
			}

			InputDialog inputDialog = new AdvancedInputDialog();

			Object comment = inputDialog.showInputDialog(parent, "Edit a comment", message, currentComment);
			if (comment != null) {
				String newComment = comment.toString().replaceAll(System.lineSeparator(), " ").replaceAll("\\n", " ")
						.replaceAll("\\{", "(").replaceAll("\\}", ")");
				if (!newComment.equals(currentComment)) {
					if (game.getNumOfPlies() > 0) {
						game.setPreMoveComment(newComment);
					} else {
						game.setEmptyGameComment(newComment);
					}
					textViewer.moveModelChanged(null);
				}
			}
		}
	}

	@SuppressWarnings("serial")
	private static class CommentAfterAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;
		private final Component parent;

		private CommentAfterAction(Game game, GameTextViewer textViewer, Component parent) {
			super("Comment after move");
			this.game = game;
			this.textViewer = textViewer;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			String currentComment = game.getPostMoveComment();
			String move = game.getLastMoveAsSanWithNumber();
			String message;
			if (move == null || move.isEmpty()) {
				message = "Your comment:";
			} else {
				message = "Your comment after " + move + ":";
			}

			InputDialog inputDialog = new AdvancedInputDialog();

			Object comment = inputDialog.showInputDialog(parent, "Edit a comment", message, currentComment);
			if (comment != null) {
				String newComment = comment.toString().replaceAll(System.lineSeparator(), " ").replaceAll("\\n", " ")
						.replaceAll("\\{", "(").replaceAll("\\}", ")");
				if (!newComment.equals(currentComment)) {
					if (game.getNumOfPlies() > 0) {
						game.setPostMoveComment(newComment);
					} else {
						game.setEmptyGameComment(newComment);
					}
					textViewer.moveModelChanged(null);
				}
			}
		}
	}

	@SuppressWarnings("serial")
	private static class ResultAction extends AbstractAction {
		private final Game game;
		private final String pgnResult;

		private ResultAction(Game game, String pgnResult) {
			super(pgnResult);
			this.game = game;
			this.pgnResult = pgnResult;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.setTag(PGN.TAG_RESULT, pgnResult);
		}
	}

	@SuppressWarnings("serial")
	private static class EventAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private EventAction(Game game, Component parent) {
			super("Set event");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object evnt = JOptionPane.showInputDialog(parent, "Event", "Set event", JOptionPane.PLAIN_MESSAGE, null, null,
					game.getEvent());
			if (evnt != null) {
				game.setTag(PGN.TAG_EVENT, evnt.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class SiteAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private SiteAction(Game game, Component parent) {
			super("Set site");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object site = JOptionPane.showInputDialog(parent, "Site", "Set site", JOptionPane.PLAIN_MESSAGE, null, null,
					game.getSite());
			if (site != null) {
				game.setTag(PGN.TAG_SITE, site.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class DateAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private DateAction(Game game, Component parent) {
			super("Set date");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object date = JOptionPane.showInputDialog(parent, "Date (yyyy.mm.dd, with ? for missing information)", "Set date",
					JOptionPane.PLAIN_MESSAGE, null, null, game.getDate());
			if (date != null) {
				game.setTag(PGN.TAG_DATE, date.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class RoundAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private RoundAction(Game game, Component parent) {
			super("Set round");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object round = JOptionPane.showInputDialog(parent, "Round (please use PGN format)", "Set round",
					JOptionPane.PLAIN_MESSAGE, null, null, game.getRound());
			if (round != null) {
				game.setTag(PGN.TAG_ROUND, round.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class WhiteAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private WhiteAction(Game game, Component parent) {
			super("Set white player");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object white = JOptionPane.showInputDialog(parent, "White (family name, forenames (or initials)", "Set white player",
					JOptionPane.PLAIN_MESSAGE, null, null, game.getWhite());
			if (white != null) {
				game.setTag(PGN.TAG_WHITE, white.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class BlackAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private BlackAction(Game game, Component parent) {
			super("Set black player");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object black = JOptionPane.showInputDialog(parent, "Black (family name, forenames (or initials)", "Set black player",
					JOptionPane.PLAIN_MESSAGE, null, null, game.getBlack());
			if (black != null) {
				game.setTag(PGN.TAG_BLACK, black.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class EcoAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private EcoAction(Game game, Component parent) {
			super("Set ECO code");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object eco = JOptionPane.showInputDialog(parent, "ECO", "Set ECO code", JOptionPane.PLAIN_MESSAGE, null, null,
					game.getECO());
			if (eco != null) {
				game.setTag(PGN.TAG_ECO, eco.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class FenAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private FenAction(Game game, Component parent) {
			super("Set FEN");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object fen = JOptionPane.showInputDialog(parent, "FEN", "Set start position by FEN", JOptionPane.PLAIN_MESSAGE, null,
					null, game.getTag(PGN.TAG_FEN));
			if (fen != null) {
				try {
					game.setGameByFEN(fen.toString(), false);
				} catch (InvalidFenException ex) {
					JOptionPane.showMessageDialog(parent, ex.getMessage(), "Invalid FEN", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}

	@SuppressWarnings("serial")
	private static class EventDateAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private EventDateAction(Game game, Component parent) {
			super("Set event date");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object eventDate = JOptionPane.showInputDialog(parent, "Event date", "Set the event date", JOptionPane.PLAIN_MESSAGE,
					null, null, game.getTag(PGN.TAG_EVENT_DATE));
			if (eventDate != null) {
				game.setTag(PGN.TAG_EVENT_DATE, eventDate.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class WhitesEloAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private WhitesEloAction(Game game, Component parent) {
			super("Set White's ELO");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object whiteElo = JOptionPane.showInputDialog(parent, "White's ELO", "Set the White's ELO", JOptionPane.PLAIN_MESSAGE,
					null, null, game.getTag(PGN.TAG_WHITE_ELO));
			if (whiteElo != null) {
				game.setTag(PGN.TAG_WHITE_ELO, whiteElo.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class BlacksEloAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private BlacksEloAction(Game game, Component parent) {
			super("Set Black's ELO");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object blackElo = JOptionPane.showInputDialog(parent, "Black's ELO", "Set the Black's ELO", JOptionPane.PLAIN_MESSAGE,
					null, null, game.getTag(PGN.TAG_BLACK_ELO));
			if (blackElo != null) {
				game.setTag(PGN.TAG_BLACK_ELO, blackElo.toString());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class PromoteVariationAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private PromoteVariationAction(Game game, Component parent) {
			super("Promote variation");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (!game.promoteVariation()) {
				String message = "Ooops. A malfuction happened. Please report the game and move to the program's author.";
				JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	@SuppressWarnings("serial")
	private static class NullMoveAction extends AbstractAction {

		private final Game game;
		private final Component parent;

		private NullMoveAction(Game game, Component parent) {
			super("Add null move");
			this.game = game;
			this.parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				if (!game.getPosition().isCheck()) {
					game.getPosition().doMove(Move.NULL_MOVE);
				} else {
					String message = (game.getPosition().getToPlay() == Chess.WHITE ? "White " : "Black ") + "is in check.";
					JOptionPane.showMessageDialog(parent, message, "Null move is illegal", JOptionPane.ERROR_MESSAGE);
				}
			} catch (IllegalMoveException ex) {
				ex.printStackTrace();
			}
		}
	}

	@SuppressWarnings("serial")
	private static class DeleteRemainingMovesAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;

		private DeleteRemainingMovesAction(Game game, GameTextViewer textViewer) {
			super("Delete remaining moves");
			this.game = game;
			this.textViewer = textViewer;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.deleteRemainingMoves();
			textViewer.showCurrentGameNode();
		}
	}

	@SuppressWarnings("serial")
	private static class DeleteThisVariationAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;

		private DeleteThisVariationAction(Game game, GameTextViewer textViewer) {
			super("Delete this variation");
			this.game = game;
			this.textViewer = textViewer;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.deleteCurrentLine();
			textViewer.showCurrentGameNode();
		}
	}

	@SuppressWarnings("serial")
	private static class StripAllCommentsAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;

		private StripAllCommentsAction(Game game, GameTextViewer textViewer) {
			super("Strip all comments");
			this.game = game;
			this.textViewer = textViewer;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.removeAllComments();
			textViewer.showCurrentGameNode();
		}
	}

	@SuppressWarnings("serial")
	private static class StripAllVariationsAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;

		private StripAllVariationsAction(Game game, GameTextViewer textViewer) {
			super("Strip all variations");
			this.game = game;
			this.textViewer = textViewer;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.deleteAllLines();
			textViewer.showCurrentGameNode();
		}
	}

	@SuppressWarnings("serial")
	private static class StripAllNagsAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;

		private StripAllNagsAction(Game game, GameTextViewer textViewer) {
			super("Strip all NAGs (!?, +-, etc)");
			this.game = game;
			this.textViewer = textViewer;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.removeAllNags();
			textViewer.showCurrentGameNode();
		}
	}

	@SuppressWarnings("serial")
	private static class StripAllAction extends AbstractAction {

		private final Game game;
		private final GameTextViewer textViewer;

		private StripAllAction(Game game, GameTextViewer textViewer) {
			super("Strip all");
			this.game = game;
			this.textViewer = textViewer;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			game.stripAll();
			textViewer.showCurrentGameNode();
		}
	}

}
