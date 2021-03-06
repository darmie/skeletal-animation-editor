package com.gemserk.tools.animationeditor.main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.lwjgl.opengl.GL14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.gemserk.animation4j.timeline.Timeline;
import com.gemserk.animation4j.timeline.TimelineAnimation;
import com.gemserk.commons.gdx.camera.Camera;
import com.gemserk.commons.gdx.camera.CameraImpl;
import com.gemserk.commons.gdx.camera.Libgdx2dCamera;
import com.gemserk.commons.gdx.camera.Libgdx2dCameraTransformImpl;
import com.gemserk.commons.gdx.graphics.ImmediateModeRendererUtils;
import com.gemserk.commons.gdx.resources.LibgdxResourceBuilder;
import com.gemserk.componentsengine.input.InputDevicesMonitorImpl;
import com.gemserk.componentsengine.input.LibgdxInputMappingBuilder;
import com.gemserk.resources.Resource;
import com.gemserk.resources.ResourceManager;
import com.gemserk.tools.animationeditor.core.Joint;
import com.gemserk.tools.animationeditor.core.JointImpl;
import com.gemserk.tools.animationeditor.core.Skeleton;
import com.gemserk.tools.animationeditor.core.SkeletonAnimation;
import com.gemserk.tools.animationeditor.core.SkeletonAnimationKeyFrame;
import com.gemserk.tools.animationeditor.core.Skin;
import com.gemserk.tools.animationeditor.core.SkinPatch;
import com.gemserk.tools.animationeditor.core.tree.AnimationEditor;
import com.gemserk.tools.animationeditor.core.tree.SkeletonEditor;
import com.gemserk.tools.animationeditor.utils.JointUtils;
import com.gemserk.util.ScreenshotSaver;

public class EditorLibgdxApplicationListener extends Game {

	protected static final Logger logger = LoggerFactory.getLogger(EditorLibgdxApplicationListener.class);

	private static class Colors {

		public static final Color lineColor = new Color(1f, 1f, 1f, 1f);
		public static final Color nodeColor = new Color(1f, 1f, 1f, 1f);
		public static final Color selectedNodeColor = new Color(0f, 0f, 1f, 1f);
		public static final Color nearNodeColor = new Color(1f, 0f, 0f, 1f);

		public static final Color spatialColor = new Color(0f, 1f, 0f, 1f);

	}

	private static class Actions {

		public static final String LeftMouseButton = "leftMouseButton";
		public static final String RightMouseButton = "rightMouseButton";
		public static final String DeleteNodeButton = "deleteNodeButton";
		public static final String RotateButton = "rotateButton";

		public static final String ModifySkinPatchButton = "modifySkinPatchButton";
		public static final String CancelStateButton = "cancelStateButton";

		public static final String SecondActionButton = "secondActionButton";

		public static final String ZoomIn = "zoomIn";
		public static final String ZoomOut = "zoomOut";

		public static final String InsertJoint = "insertJoint";

	}

	SpriteBatch spriteBatch;

	Joint nearNode;

	float nodeSize = 2f;
	float backgroundNodeSize = 4f;

	float selectDistance = 4f;

	Vector2 position = new Vector2();

	SkeletonEditor skeletonEditor;
	AnimationEditor animationEditor;

	ResourceManager<String> resourceManager;
	LibgdxResourceBuilder resourceBuilder;

	Libgdx2dCamera libgdxCamera;
	Camera camera;

	private InputDevicesMonitorImpl<String> inputMonitor;

	public void setTreeEditor(SkeletonEditor skeletonEditor) {
		this.skeletonEditor = skeletonEditor;
	}

	public void setAnimationEditor(AnimationEditor animationEditor) {
		this.animationEditor = animationEditor;
	}

	public void setResourceManager(ResourceManager<String> resourceManager) {
		this.resourceManager = resourceManager;
	}

	interface EditorState {

		void update();

		void render();

	}

	class PlayingAnimationState implements EditorState {

		private TimelineAnimation timelineAnimation;
		private Skeleton skeleton;

		public PlayingAnimationState() {
			SkeletonAnimation currentAnimation = animationEditor.getCurrentAnimation();

			ArrayList<SkeletonAnimationKeyFrame> keyFrames = currentAnimation.getKeyFrames();

			skeleton = skeletonEditor.getSkeleton();

			if (keyFrames.size() == 0) {
				timelineAnimation = null;
				return;
			}

			Timeline timeline = JointUtils.getTimeline(skeleton.getRoot(), keyFrames);

			timelineAnimation = new TimelineAnimation(timeline, (float) keyFrames.size() - 1);
			timelineAnimation.setSpeed(1f);
			timelineAnimation.setDelay(0f);
			timelineAnimation.start(0);
		}

		@Override
		public void update() {

			if (timelineAnimation == null)
				return;

			timelineAnimation.update(Gdx.graphics.getDeltaTime());

			if (!animationEditor.isPlayingAnimation()) {
				currentState = new NormalEditorState();
				return;
			}

		}

		@Override
		public void render() {
			renderTransparentTexture();
			renderSkin(skin);
			renderSkeleton(skeleton);
		}

	}

	class ExportingAnimationState implements EditorState {

		private TimelineAnimation timelineAnimation;
		private Skeleton skeleton;

		int sequence;
		float fps;

		String animationPath;
		private Mesh mesh;

		public ExportingAnimationState(String animationPath, float fps) {
			SkeletonAnimation currentAnimation = animationEditor.getCurrentAnimation();

			ArrayList<SkeletonAnimationKeyFrame> keyFrames = currentAnimation.getKeyFrames();

			skeleton = skeletonEditor.getSkeleton();

			if (keyFrames.size() == 0) {
				timelineAnimation = null;
				return;
			}

			Timeline timeline = JointUtils.getTimeline(skeleton.getRoot(), keyFrames);

			timelineAnimation = new TimelineAnimation(timeline, (float) keyFrames.size() - 1);
			timelineAnimation.setSpeed(1f);
			timelineAnimation.setDelay(0f);
			timelineAnimation.start(1);

			sequence = 0;
			this.fps = fps;
			this.animationPath = animationPath;

			int size = 100;
			mesh = new Mesh(VertexDataType.VertexArray, false, size * 4, size * 6, new VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE), //
					new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE), //
					new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));

			int len = size * 6;
			short[] indices = new short[len];
			short j = 0;
			for (int i = 0; i < len; i += 6, j += 4) {
				indices[i + 0] = (short) (j + 0);
				indices[i + 1] = (short) (j + 1);
				indices[i + 2] = (short) (j + 2);
				indices[i + 3] = (short) (j + 2);
				indices[i + 4] = (short) (j + 3);
				indices[i + 5] = (short) (j + 0);
			}
			mesh.setIndices(indices);
		}

		@Override
		public void update() {

		}

		@Override
		public void render() {
			if (timelineAnimation == null)
				return;

			float updateTime = 1f / fps;

			GL10 gl10 = Gdx.graphics.getGL10();

			gl10.glPushMatrix();

			gl10.glClearColor(0.0f, 0f, 0f, 0.0f);
			gl10.glClear(GL10.GL_COLOR_BUFFER_BIT);

			gl10.glEnable(GL10.GL_BLEND);
			gl10.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_DST_ALPHA);

			// GL14.glBlendFuncSeparate(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA, GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA);
			GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);

			gl10.glEnable(GL10.GL_TEXTURE_2D);

			for (int i = 0; i < skin.patchesCount(); i++) {
				SkinPatch patch = skin.getPatch(i);

				String jointId = patch.getJointId();

				Sprite patchSprite = skinSprites.get(jointId).get();

				patchSprite.getTexture().bind();

				mesh.setVertices(patchSprite.getVertices());
				mesh.render(GL10.GL_TRIANGLES, 0, 1 * 6);
			}

			gl10.glPushMatrix();

			try {
				ScreenshotSaver.saveScreenshot(new File(animationPath + sequence + ".png"), true);
			} catch (IOException e) {
				logger.error("failed to save frame for animation ", e);
			}

			sequence++;

			timelineAnimation.update(updateTime);

			if (timelineAnimation.isFinished())
				currentState = new NormalEditorState();

		}

	}

	class NormalEditorState implements EditorState {

		private final Vector2 mousePosition = new Vector2();

		public NormalEditorState() {
			logger.debug("Current state: none");
		}

		public void update() {

			// int x = Gdx.input.getX();
			// int y = Gdx.graphics.getHeight() - Gdx.input.getY();

			mousePosition.set(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());

			libgdxCamera.unproject(mousePosition);

			position.set(nearNode.getX(), nearNode.getY());

			if (animationEditor.isPlayingAnimation()) {
				currentState = new PlayingAnimationState();
				return;
			}

			if (inputMonitor.getButton(Actions.RightMouseButton).isReleased())
				skeletonEditor.select(nearNode);

			if (inputMonitor.getButton(Actions.ModifySkinPatchButton).isReleased()) {
				currentState = new ModifyingSkinPatchState();
				return;
			}

			if (inputMonitor.getButton(Actions.DeleteNodeButton).isReleased()) {
				if (!skeletonEditor.isSelectedNode(skeletonEditor.getSkeleton().getRoot())) {
					Joint selectedJoint = skeletonEditor.getSelectedNode();
					if (selectedJoint != null) {
						skeletonEditor.remove(selectedJoint);
						skin.removePatch(selectedJoint);
					}
				}
			}

			if (inputMonitor.getButton(Actions.RotateButton).isPressed()) {
				currentState = new RotatingJointState();
			}

			if (inputMonitor.getButton(Actions.LeftMouseButton).isPressed()) {
				if (position.dst(mousePosition.x, mousePosition.y) < selectDistance) {
					skeletonEditor.select(nearNode);
					currentState = new DraggingJointState();
					return;
				} else {
					currentState = new DraggingCameraState();
					return;
				}
			}

			if (inputMonitor.getButton(Actions.InsertJoint).isReleased()) {
				Joint newNode = new JointImpl("node-" + MathUtils.random(111, 999));
				skeletonEditor.add(newNode);
				newNode.setPosition(mousePosition.x, mousePosition.y);
				skeletonEditor.select(newNode.getParent());
			}

			if (inputMonitor.getButton(Actions.ZoomIn).isReleased()) {
				camera.setZoom(camera.getZoom() * 2f);
			}

			if (inputMonitor.getButton(Actions.ZoomOut).isReleased()) {
				camera.setZoom(camera.getZoom() * 0.5f);
			}

		}

		@Override
		public void render() {
			renderTransparentTexture();
			renderSkin(skin);
			renderSkeleton(skeletonEditor.getSkeleton());
		}

	}

	class DraggingCameraState implements EditorState {

		private final Vector2 previousPosition = new Vector2();

		public DraggingCameraState() {
			previousPosition.set(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
			logger.debug("Current state: dragging camera");
		}

		@Override
		public void update() {
			position.set(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());

			if (inputMonitor.getButton(Actions.LeftMouseButton).isReleased()) {
				currentState = new NormalEditorState();
				return;
			}

			Vector2 diff = new Vector2(previousPosition);
			diff.sub(position);

			diff.mul(1f / camera.getZoom());

			camera.setPosition(camera.getX() + diff.x, camera.getY() + diff.y);

			previousPosition.set(position);
		}

		@Override
		public void render() {
			renderTransparentTexture();
			renderSkin(skin);
			renderSkeleton(skeletonEditor.getSkeleton());
		}

	}

	class DraggingJointState implements EditorState {

		private final Vector2 position = new Vector2();
		private final Vector2 mousePosition = new Vector2();

		public DraggingJointState() {
			position.set(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
			libgdxCamera.unproject(position);
			logger.debug("Current state: dragging joint");
		}

		@Override
		public void update() {
			mousePosition.set(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
			libgdxCamera.unproject(mousePosition);

			if (inputMonitor.getButton(Actions.LeftMouseButton).isReleased()) {
				currentState = new NormalEditorState();
				return;
			}

			skeletonEditor.moveSelected(mousePosition.x - position.x, mousePosition.y - position.y);

			position.set(mousePosition);
		}

		@Override
		public void render() {
			renderTransparentTexture();
			renderSkin(skin);
			renderSkeleton(skeletonEditor.getSkeleton());
		}

	}

	class RotatingJointState implements EditorState {

		private int currentY;
		float rotationSpeed = 1f;

		public RotatingJointState() {
			currentY = Gdx.graphics.getHeight() - Gdx.input.getY();
			logger.debug("Current state: rotating joint");
		}

		@Override
		public void update() {

			int y = Gdx.graphics.getHeight() - Gdx.input.getY();

			if (inputMonitor.getButton(Actions.RotateButton).isReleased()) {
				currentState = new NormalEditorState();
				return;
			}

			float rotation = (float) (currentY - y) * rotationSpeed;
			skeletonEditor.rotateSelected(rotation);

			currentY = y;
		}

		@Override
		public void render() {
			renderTransparentTexture();
			renderSkin(skin);
			renderSkeleton(skeletonEditor.getSkeleton());
		}

	}

	class ModifyingSkinPatchState implements EditorState {

		Vector2 position = new Vector2();
		Vector2 positionModifier = new Vector2();

		float previousY;

		float rotationSpeed = 1f;

		public ModifyingSkinPatchState() {
			position.x = Gdx.input.getX();
			position.y = Gdx.graphics.getHeight() - Gdx.input.getY();

			previousY = Gdx.graphics.getHeight() - Gdx.input.getY();

			logger.debug("Current state: modifying skin patch");
		}

		@Override
		public void update() {

			Joint selectedNode = skeletonEditor.getSelectedNode();
			Sprite nodeSprite = skinSprites.get(selectedNode.getId()).get();

			if (inputMonitor.getButton(Actions.CancelStateButton).isReleased()) {
				currentState = new NormalEditorState();
				return;
			}

			SkinPatch skinPatch = skin.getPatch(selectedNode);

			if (inputMonitor.getButton(Actions.RotateButton).isHolded()) {
				int currentY = Gdx.graphics.getHeight() - Gdx.input.getY();
				float rotation = (float) (currentY - previousY) * rotationSpeed;
				skinPatch.angle += rotation;
			} else if (inputMonitor.getButton(Actions.LeftMouseButton).isHolded()) {

				positionModifier.x = Gdx.input.getX();
				positionModifier.y = Gdx.graphics.getHeight() - Gdx.input.getY();

				skinPatch.project(positionModifier, nodeSprite);

				positionModifier.sub(position);

				positionModifier.x /= nodeSprite.getWidth();
				positionModifier.y /= nodeSprite.getHeight();

				skinPatch.center.x -= positionModifier.x;
				skinPatch.center.y += positionModifier.y;
			}

			position.x = Gdx.input.getX();
			position.y = Gdx.graphics.getHeight() - Gdx.input.getY();

			previousY = Gdx.graphics.getHeight() - Gdx.input.getY();

			skinPatch.project(position, nodeSprite);
		}

		@Override
		public void render() {
			renderTransparentTexture();
			renderSkin(skin);
			renderSkeleton(skeletonEditor.getSkeleton());
		}

	}

	EditorState currentState;
	Skin skin;

	Map<String, Resource<Sprite>> skinSprites;

	public Map<String, String> texturePaths = new HashMap<String, String>();

	Texture transparentTexture;

	public void setCurrentSkin(final File file) {

		Gdx.app.postRunnable(new Runnable() {

			@Override
			public void run() {
				Joint selectedNode = skeletonEditor.getSelectedNode();

				if (selectedNode == null)
					return;

				String textureId = "texture" + texturePaths.size();

				resourceBuilder.resource(textureId, resourceBuilder //
						.texture2(Gdx.files.absolute(file.getAbsolutePath()))//
						.minFilter(TextureFilter.Linear) //
						.magFilter(TextureFilter.Linear));

				String jointId = selectedNode.getId();

				resourceBuilder.sprite(jointId, textureId);

				Resource<Sprite> resource = resourceManager.get(jointId);

				skin.addPatch(jointId, textureId);
				skinSprites.put(jointId, resource);

				texturePaths.put(textureId, file.getAbsolutePath());
			}
		});

	}

	@Override
	public void create() {
		Gdx.graphics.setVSync(true);

		Gdx.graphics.getGL10().glClearColor(1f, 1f, 1f, 0f);

		Texture.setEnforcePotImages(false);

		currentState = new NormalEditorState();

		spriteBatch = new SpriteBatch();
		spriteBatch.setBlendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		inputMonitor = new InputDevicesMonitorImpl<String>();

		new LibgdxInputMappingBuilder<String>(inputMonitor, Gdx.input) {
			{
				monitorMouseLeftButton(Actions.LeftMouseButton);
				monitorMouseRightButton(Actions.RightMouseButton);
				monitorKeys(Actions.DeleteNodeButton, Keys.DEL, Keys.BACKSPACE);

				monitorKey(Actions.RotateButton, Keys.CONTROL_LEFT);

				monitorKey(Actions.ModifySkinPatchButton, Keys.R);
				monitorKey(Actions.CancelStateButton, Keys.ESCAPE);

				monitorKey(Actions.SecondActionButton, Keys.SHIFT_LEFT);

				monitorKey(Actions.ZoomIn, Keys.UP);
				monitorKey(Actions.ZoomOut, Keys.DOWN);

				monitorKey(Actions.InsertJoint, Keys.PLUS);
			}
		};

		resourceBuilder = new LibgdxResourceBuilder(resourceManager);

		transparentTexture = resourceBuilder.texture2(Gdx.files.internal("data/transparent.png")) //
			.textureWrap(TextureWrap.Repeat, TextureWrap.Repeat) //
			.build();
		
		newProject();
	}

	public void newProject() {
		Joint root = new JointImpl("root");

		root.setPosition(0f, 0f);
		root.setAngle(0f);
		
		animationEditor.setCurrentAnimation(new SkeletonAnimation());

		skeletonEditor.setSkeleton(new Skeleton(root));
		skeletonEditor.select(root);

		skin = new Skin();
		skinSprites = new HashMap<String, Resource<Sprite>>();
		texturePaths = new HashMap<String, String>();

		libgdxCamera = new Libgdx2dCameraTransformImpl(Gdx.graphics.getWidth() * 0.5f, Gdx.graphics.getHeight() * 0.5f);
		libgdxCamera.zoom(1f);

		camera = new CameraImpl(0f, 0f, 1f, 0f);
		
		resourceManager.unloadAll();
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		System.out.println("game.resize " + width + "x" + height);
		Gdx.gl.glViewport(0, 0, width, height);

		libgdxCamera = new Libgdx2dCameraTransformImpl(Gdx.graphics.getWidth() * 0.5f, Gdx.graphics.getHeight() * 0.5f);
		libgdxCamera.zoom(1f);
	}

	@Override
	public void render() {
		realUpdate();
		realRender();
	}

	private void realUpdate() {
		inputMonitor.update();

		position.set(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());

		libgdxCamera.unproject(position);

		nearNode = skeletonEditor.getNearestNode(position.x, position.y);

		currentState.update();

		updateSkin();
	}

	private void updateSkin() {
		ArrayList<SkinPatch> patchList = skin.getPatchList();
		for (int i = 0; i < patchList.size(); i++) {
			SkinPatch patch = patchList.get(i);
			String jointId = patch.getJointId();
			Joint joint = skeletonEditor.getSkeleton().find(jointId);
			Resource<Sprite> resource = skinSprites.get(jointId);
			patch.update(resource.get(), joint);
		}
	}

	private void realRender() {
		libgdxCamera.move(camera.getX(), camera.getY());
		libgdxCamera.zoom(camera.getZoom());

		GL10 gl10 = Gdx.graphics.getGL10();

		gl10.glClearColor(1f, 1f, 1f, 0f);
		gl10.glClear(GL10.GL_COLOR_BUFFER_BIT);

		currentState.render();
	}

	private void renderTransparentTexture() {
		float textureWidth = transparentTexture.getWidth() / camera.getZoom();
		float textureHeight = transparentTexture.getHeight() / camera.getZoom();

		float width = Gdx.graphics.getWidth() / camera.getZoom();
		float height = Gdx.graphics.getHeight() / camera.getZoom();

		spriteBatch.setProjectionMatrix(libgdxCamera.getCombinedMatrix());
		spriteBatch.begin();
		spriteBatch.disableBlending();
		spriteBatch.draw(transparentTexture, camera.getX() - width / 2, camera.getY() - height / 2, width, height, 0f, 0f, width / textureWidth, height / textureHeight);
		spriteBatch.enableBlending();
		spriteBatch.end();
	}

	private void renderSkin(Skin skin) {
		spriteBatch.setProjectionMatrix(libgdxCamera.getCombinedMatrix());
		spriteBatch.begin();
		for (int i = 0; i < skin.patchesCount(); i++) {
			SkinPatch patch = skin.getPatch(i);

			String jointId = patch.getJointId();

			Sprite patchSprite = skinSprites.get(jointId).get();
			patchSprite.draw(spriteBatch);
		}
		spriteBatch.end();
	}

	private void renderSkeleton(Skeleton skeleton) {
		ImmediateModeRendererUtils.getProjectionMatrix().set(libgdxCamera.getCombinedMatrix());
		renderNodeTree(skeleton.getRoot());
	}

	private void renderNodeTree(Joint joint) {
		renderNodeOnly(joint);
		ArrayList<Joint> children = joint.getChildren();
		for (int i = 0; i < children.size(); i++) {
			Joint child = children.get(i);
			ImmediateModeRendererUtils.drawLine(joint.getX(), joint.getY(), child.getX(), child.getY(), Colors.lineColor);
			renderNodeTree(child);
		}
	}

	private void renderNodeOnly(Joint joint) {
		if (joint == nearNode) {
			ImmediateModeRendererUtils.fillRectangle(joint.getX() - backgroundNodeSize, //
					joint.getY() - backgroundNodeSize, //
					joint.getX() + backgroundNodeSize, //
					joint.getY() + backgroundNodeSize, //
					Colors.nearNodeColor);
		}

		if (skeletonEditor.isSelectedNode(joint)) {
			float backgroundNodeSize = 4f;
			ImmediateModeRendererUtils.fillRectangle(joint.getX() - backgroundNodeSize, //
					joint.getY() - backgroundNodeSize, //
					joint.getX() + backgroundNodeSize, //
					joint.getY() + backgroundNodeSize, //
					Colors.selectedNodeColor);
		}

		ImmediateModeRendererUtils.fillRectangle(joint.getX() - nodeSize, joint.getY() - nodeSize, joint.getX() + nodeSize, joint.getY() + nodeSize, //
				Colors.nodeColor);
	}

	private void renderPoint(float x, float y) {
		ImmediateModeRendererUtils.fillRectangle(x - nodeSize, y - nodeSize, x + nodeSize, y + nodeSize, //
				Colors.spatialColor);
	}

	@Override
	public void dispose() {
		super.dispose();
		resourceManager.unloadAll();
	}

	public void updateResources(final Skeleton skeleton, final Map<String, String> newTexturePaths, final Skin newSkin) {

		Gdx.app.postRunnable(new Runnable() {

			@Override
			public void run() {

				skeletonEditor.setSkeleton(skeleton);

				skinSprites.clear();
				texturePaths.clear();
				texturePaths.putAll(newTexturePaths);

				skin = newSkin;

				resourceManager.unloadAll();

				Set<String> keySet = texturePaths.keySet();

				for (String textureId : keySet) {
					resourceBuilder.resource(textureId, resourceBuilder //
							.texture2(Gdx.files.absolute(texturePaths.get(textureId)))//
							.minFilter(TextureFilter.Linear) //
							.magFilter(TextureFilter.Linear) //
							);
				}

				ArrayList<SkinPatch> patchList = skin.getPatchList();
				for (int i = 0; i < patchList.size(); i++) {
					SkinPatch patch = patchList.get(i);
					String jointId = patch.getJointId();
					String textureId = patch.getTextureId();

					resourceBuilder.sprite(jointId, textureId);

					Resource<Sprite> resource = resourceManager.get(jointId);
					skinSprites.put(jointId, resource);
				}

			}
		});

	}

	public void exportAnimation(final String animationPath) {

		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				currentState = new ExportingAnimationState(animationPath, 2f);
			}
		});

	}
}