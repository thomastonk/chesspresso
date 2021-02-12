package chesspresso.position;

import java.awt.event.MouseEvent;

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.ImmutablePosition.Validity;

public class OneClickMove {

	private OneClickMove() {
	}

	public static boolean squareClicked(Position position, int sqi, MouseEvent e) {
		if (position.getValidity() != Validity.IS_VALID) {
			return false;
		}
		short[] moves = position.getAllMoves();
		// test for unique to-square
		short uniqueMove = getUniqueMove(sqi, moves, true);
		if (uniqueMove != Move.NO_MOVE) {
			try {
				position.doMove(uniqueMove);
				return true;
			} catch (IllegalMoveException ignore) {
				return false;
			}
		}
		// test for unique from-square
		uniqueMove = getUniqueMove(sqi, moves, false);
		if (uniqueMove != Move.NO_MOVE) {
			try {
				position.doMove(uniqueMove);
				return true;
			} catch (IllegalMoveException ignore) {
				return false;
			}
		}
		return false;
	}

	private static short getUniqueMove(int sqi, short[] moves, boolean to) {
		short uniqueMove = Move.NO_MOVE;
		boolean isPromotion = false;
		short proCounter = 0;
		for (short move : moves) {
			if ((to && (sqi == Move.getToSqi(move))) || (!to && (sqi == Move.getFromSqi(move)))) {
				if (uniqueMove != Move.NO_MOVE) { // not unique
					if (isPromotion && Move.isPromotion(move)) { // both are promotions
						++proCounter;
						if (Move.getPromotionPiece(move) == Chess.QUEEN) {
							uniqueMove = move;
						}
						continue;
					} else {
						return Move.NO_MOVE;
					}
				}
				uniqueMove = move;
				isPromotion = Move.isPromotion(uniqueMove);
			}
		}
		if (!isPromotion || proCounter == 3) {
			// If isPromotion is true and there are exactly three other promotion moves,
			// then we return the promotion to a queen (see above).
			return uniqueMove;
		} else {
			return Move.NO_MOVE;
		}
	}

}
