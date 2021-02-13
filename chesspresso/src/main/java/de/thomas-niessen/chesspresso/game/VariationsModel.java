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
package chesspresso.game;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

/*
 * TN: I introduced this class to encapsulate the code for modifications of
 * the variations like they appeared first in promoting/grading up a variation.
 * Start point is always the short[] m_moves from GameMoveModel, which contains
 * moves, comments, NAGs and the line structure information (and which should
 * one day be named m_nodes).
 */
public class VariationsModel {

	private static final short VAR_BEGIN = GameMoveModel.LINE_START;
	private static final short VAR_END = GameMoveModel.LINE_END;

	private final short[] nodes;
	private final int size;
	private final TreeMap<Integer, Integer> variations; // begin index -> end index
	private final Map<Integer, List<Integer>> siblings;
	// example: 1.d4 e6 (1.. Nf6) (1.. f5) (1.. d5):
	// Here the three variations will be stored as siblings by their begin indices, where the
	// keys are the begin indices of these siblings. So, access with begin indices is okay.
	private final boolean valid;

	public VariationsModel(short[] nodes, int size) {
		this.nodes = nodes;
		this.size = size;
		variations = new TreeMap<>();
		siblings = new HashMap<>();
		valid = init();
	}

	private boolean init() {
		// initialize variations
		Stack<Integer> varBegins = new Stack<>();
		for (Integer i = 0; i < size; ++i) {
			if (nodes[i] == VAR_BEGIN) {
				varBegins.push(i);
			} else if (nodes[i] == VAR_END) {
				try {
					variations.put(varBegins.pop(), i);
				} catch (EmptyStackException e) {
					return false;
				}
			}
		}
		// initialize siblings
		for (Integer i = 0; i < size; ++i) {
			if (nodes[i] == VAR_BEGIN && !siblings.containsKey(i)) {
				List<Integer> list = new ArrayList<>();
				list.add(i);
				Integer end = variations.get(i);
				while (variations.containsKey(end + 1)) {
					list.add(end + 1);
					end = variations.get(end + 1);
				}
				for (Integer j : list) {
					siblings.put(j, list);
				}
			}
		}
		return true;
	}

	public boolean isValid() {
		return valid;
	}

	public Map.Entry<Integer, Integer> getVariation(Integer index) {
		Map.Entry<Integer, Integer> entry = variations.floorEntry(index);
		while (entry.getValue() < index) {
			entry = variations.floorEntry(entry.getKey() - 1);
		}
		return entry;
	}

	public List<Integer> getSiblings(Integer index) {
		return siblings.get(index);
	}

	@SuppressWarnings("unused")
	private void dump() {
		System.out.println("Variations");
		for (Map.Entry<Integer, Integer> entry : variations.entrySet()) {
			System.out.println(entry.getKey() + ":" + entry.getValue());
		}
		System.out.println("Siblings");
		for (Map.Entry<Integer, List<Integer>> entry : siblings.entrySet()) {
			StringBuilder sb = new StringBuilder();
			for (Integer i : entry.getValue()) {
				sb.append(i).append(" ");
			}
			System.out.println(entry.getKey() + ": " + sb.toString());
		}
	}
}
