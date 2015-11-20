/**
 * This file is part of SADL, a library for learning all sorts of (timed) automata and performing sequence-based anomaly detection.
 * Copyright (C) 2013-2015  the original author or authors.
 *
 * SADL is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SADL is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SADL.  If not, see <http://www.gnu.org/licenses/>.
 */

package sadl.models.PTA;

import org.apache.commons.lang3.Range;

import jsat.distributions.empirical.NormalRandomized;

public class SubEvent {

	protected Event event;
	protected float subEventNumber;
	protected Range<Double> anomalyInterval;
	protected Range<Double> warningInterval;
	protected Range<Double> boundInterval;
	protected double expectedValue;
	protected double deviation;

	protected NormalRandomized normalFunction;

	protected SubEvent previousSubEvent;
	protected SubEvent nextSubEvent;

	public SubEvent(Event event, float subEventNumber, double expectedValue, double deviation, Range<Double> boundInterval, Range<Double> anomalyInterval,
			Range<Double> warningInterval) {

		this.event = event;
		this.subEventNumber = subEventNumber;
		this.expectedValue = expectedValue;
		this.deviation = deviation;

		if (deviation > 0){
			normalFunction = new NormalRandomized(expectedValue, deviation);
		}

		this.boundInterval = boundInterval;
		this.anomalyInterval = anomalyInterval;
		this.warningInterval = warningInterval;
	}

	public String getSymbol() {

		return event.getSymbol() + subEventNumber;
	}

	public boolean isAnomaly(double time) {

		double left;
		final double right;

		if (this.hasLeftCriticalArea()) {
			left = this.getLeftBound();
		} else {
			left = this.anomalyInterval.getMinimum();
		}

		if (this.hasRightCriticalArea()) {
			right = this.getRightBound();
		} else {
			right = this.anomalyInterval.getMaximum();
		}

		if (left == time || (left < time && time < right)) {
			return false;
		}

		return true;
	}

	public boolean isInCriticalArea(double time) {

		if (this.isAnomaly(time)) {
			return false;
		}

		if (isInLeftCriticalArea(time) || isInRightCriticalArea(time)) {
			return true;
		}

		return false;
	}

	public boolean isInLeftCriticalArea(double time) {
		if (this.hasLeftCriticalArea() && time < this.getLeftBound()) {
			return true;
		}

		return false;
	}

	public boolean isInRightCriticalArea(double time) {
		if (this.hasRightCriticalArea() && this.getRightBound() >= time) {
			return true;
		}

		return false;
	}

	public boolean isInBounds(double time) {
		final double leftBound = this.getLeftBound();
		final double rightBound = this.getRightBound();

		if (leftBound == time || (leftBound < time && time < rightBound)) {
			return true;
		}

		return false;
	}

	public float getNumber() {

		return subEventNumber;
	}

	public double getExpectedValue() {

		return expectedValue;
	}

	public double getDeviation() {

		return deviation;
	}

	public double getLeftBound() {

		return boundInterval.getMinimum();
	}

	public double getRightBound() {

		return boundInterval.getMaximum();
	}

	public double getLeftAnomalyBound() {

		return anomalyInterval.getMinimum();
	}

	public double getRightAnomalyBound() {

		return anomalyInterval.getMaximum();
	}

	public double getLeftIntervalInState(PTAState state) {

		if (this.hasLeftCriticalArea() && state.outTransitions.containsKey(this.getPreviousSubEvent().getSymbol())) {
			return this.getLeftBound();
		}

		return this.anomalyInterval.getMinimum();

	}

	public double getLeftInterval() {

		if (this.hasLeftCriticalArea()) {
			return this.getLeftBound();
		}

		return this.anomalyInterval.getMinimum();

	}

	public double getRightIntervalInState(PTAState state) {

		if (this.hasRightCriticalArea() && state.outTransitions.containsKey(this.getNextSubEvent().getSymbol())) {
			return this.getRightBound();
		}

		return this.anomalyInterval.getMaximum();
	}

	public double getRightInterval() {

		if (this.hasRightCriticalArea()) {
			return this.getRightBound();
		}

		return this.anomalyInterval.getMaximum();
	}

	public Range<Double> getIntervalInState(PTAState state) {

		return Range.between(getLeftIntervalInState(state), getRightIntervalInState(state));
	}

	public Range<Double> getInterval() {

		return Range.between(getLeftInterval(), getRightInterval());
	}

	public boolean hasLeftCriticalArea() {

		if (anomalyInterval.getMinimum() < boundInterval.getMinimum()) {
			return true;
		}

		return false;
	}

	public boolean hasRightCriticalArea() {

		if (boundInterval.getMaximum() < anomalyInterval.getMaximum()) {
			return true;
		}

		return false;
	}

	public boolean hasWarning(double time) {
		final double leftBound = warningInterval.getMinimum();
		final double rightBound = warningInterval.getMaximum();

		if (leftBound == time || (leftBound < time && time < rightBound)) {
			return true;
		}

		return true;
	}

	public SubEvent getPreviousSubEvent() {

		return previousSubEvent;
	}

	public SubEvent getNextSubEvent() {

		return nextSubEvent;
	}

	public Event getEvent() {
		return this.event;
	}

	public double generateRandomTime(boolean allowAnomaly) {

		if (deviation == 0){
			return expectedValue;
		}

		double randomTime = 0.0d;
		final boolean condition;

		do {

			randomTime = normalFunction.getRandomPoint();

			if (randomTime >= 0.0d && !allowAnomaly && !this.isAnomaly(randomTime)) {
				return randomTime; //TODO check
			}

		} while (true);

	}

	public double calculateProbability(double time) {

		if (deviation == 0.0d){
			if (time == expectedValue){
				return 1.0d;
			}
			else{
				return 0.0d;
			}
		}

		final double probability = normalFunction.cdf(time);

		if (probability > 0.5d){
			return (1 - probability) * 2.0d;
		}

		return probability * 2.0d;
	}

	public void isolateCriticalAreas() {
		final double newMin = Math.max(boundInterval.getMinimum(), anomalyInterval.getMinimum());
		final double newMax = Math.min(boundInterval.getMaximum(), anomalyInterval.getMaximum());
		anomalyInterval = Range.between(newMin, newMax);
	}

	public void isolateLeftCriticalArea() {
		final double newMin = Math.max(boundInterval.getMinimum(), anomalyInterval.getMinimum());
		final double newMax = anomalyInterval.getMaximum();
		anomalyInterval = Range.between(newMin, newMax);
	}

	public void isolateRightCriticalArea() {
		final double newMin = anomalyInterval.getMinimum();
		final double newMax = Math.min(boundInterval.getMaximum(), anomalyInterval.getMaximum());
		anomalyInterval = Range.between(newMin, newMax);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((event == null) ? 0 : event.getSymbol() == null ? 0 : event.getSymbol().hashCode());
		result = prime * result + Float.floatToIntBits(subEventNumber);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final SubEvent other = (SubEvent) obj;
		if (event == null) {
			if (other.event != null) {
				return false;
			}
		} else if (!event.getSymbol().equals(other.event.getSymbol())) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {

		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(this.getSymbol() + "[" + anomalyInterval.getMinimum() + "[" + this.getLeftBound() + " " + this.getRightBound() + ")"
				+ anomalyInterval.getMaximum() + ")");

		return stringBuilder.toString();
	}

}
