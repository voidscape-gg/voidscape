package com.openrsc.server.model.entity.update;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;

public class Damage {

	public static final int ATTACKER_TYPE_UNKNOWN = 0;
	public static final int ATTACKER_TYPE_PLAYER = 1;
	public static final int ATTACKER_TYPE_NPC = 2;

	private Mob mob;
	private int damage;
	private int index;
	private int hitFeedbackAttackerType = ATTACKER_TYPE_UNKNOWN;
	private int hitFeedbackAttackerIndex = -1;
	private int hitFeedbackAttackerMaxHit = 0;

	public Damage(Mob mob, int damage) {
		this.mob = mob;
		this.setDamage(damage);
		this.setIndex(mob.getIndex());
	}

	public int getCurHits() {
		return mob.getSkills().getLevel(Skill.HITS.id());
	}

	public int getDamage() {
		return damage;
	}

	public void setDamage(int damage) {
		this.damage = damage;
	}

	public Damage withHitFeedback(Mob attacker, int attackerMaxHit) {
		if (attacker == null || attackerMaxHit <= 0 || attacker.getIndex() < 0) {
			return this;
		}

		this.hitFeedbackAttackerType = attacker.isPlayer() ? ATTACKER_TYPE_PLAYER : ATTACKER_TYPE_NPC;
		this.hitFeedbackAttackerIndex = attacker.getIndex();
		this.hitFeedbackAttackerMaxHit = attackerMaxHit;
		return this;
	}

	public boolean hasHitFeedback() {
		return hitFeedbackAttackerType != ATTACKER_TYPE_UNKNOWN
			&& hitFeedbackAttackerIndex >= 0
			&& hitFeedbackAttackerMaxHit > 0;
	}

	public int getHitFeedbackAttackerType() {
		return hitFeedbackAttackerType;
	}

	public int getHitFeedbackAttackerIndex() {
		return hitFeedbackAttackerIndex;
	}

	public int getHitFeedbackAttackerMaxHit() {
		return hitFeedbackAttackerMaxHit;
	}

	public int getMaxHits() {
		return mob.getSkills().getMaxStat(Skill.HITS.id());
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
