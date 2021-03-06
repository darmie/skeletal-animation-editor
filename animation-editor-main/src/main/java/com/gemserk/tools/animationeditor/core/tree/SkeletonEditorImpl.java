package com.gemserk.tools.animationeditor.core.tree;

import java.util.ArrayList;

import com.badlogic.gdx.math.Vector2;
import com.gemserk.tools.animationeditor.core.Joint;
import com.gemserk.tools.animationeditor.core.Skeleton;
import com.gemserk.tools.animationeditor.utils.JointUtils;

public class SkeletonEditorImpl implements SkeletonEditor {

	private final Vector2 position = new Vector2();

	Joint selectedNode;

	ArrayList<Joint> joints = new ArrayList<Joint>();

	Skeleton skeleton;

	public SkeletonEditorImpl() {
		skeleton = new Skeleton();
	}

	@Override
	public Skeleton getSkeleton() {
		return skeleton;
	}
	

	@Override
	public void setSkeleton(Skeleton skeleton) {
		this.skeleton = skeleton;
		joints.clear();
		joints.addAll(JointUtils.toArrayList(skeleton.getRoot()));
	}

	@Override
	public void select(Joint joint) {
		selectedNode = joint;
	}

	@Override
	public void remove(Joint joint) {
		Joint parent = joint.getParent();
		parent.getChildren().remove(joint);
		joints.remove(joint);
		if (isSelectedNode(joint))
			selectedNode = parent;
	}

	@Override
	public void add(Joint joint) {
		joint.setParent(selectedNode);
		selectedNode = joint;
		joints.add(joint);
	}

	@Override
	public Joint getNearestNode(float x, float y) {
		Joint nearNode = getSkeleton().getRoot();

		position.set(nearNode.getX(), nearNode.getY());

		float minDistance = position.dst(x, y);

		for (int i = 0; i < joints.size(); i++) {
			Joint joint = joints.get(i);
			position.set(joint.getX(), joint.getY());

			if (position.dst(x, y) < minDistance) {
				minDistance = position.dst(x, y);
				nearNode = joint;
			}
		}

		return nearNode;
	}

	@Override
	public Joint getSelectedNode() {
		return selectedNode;
	}

	@Override
	public boolean isSelectedNode(Joint joint) {
		return selectedNode == joint;
	}

	@Override
	public void moveSelected(float dx, float dy) {
		float x = selectedNode.getX();
		float y = selectedNode.getY();
		selectedNode.setPosition(x + dx, y + dy);
	}

	@Override
	public void rotateSelected(float angle) {
		float currentAngle = selectedNode.getAngle();
		selectedNode.setAngle(currentAngle + angle);
	}

}
