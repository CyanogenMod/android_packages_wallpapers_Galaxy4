package com.android.galaxy4;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.Allocation;
import android.renderscript.Matrix4f;
import android.renderscript.Mesh;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramFragmentFixedFunction;
import android.renderscript.ProgramRaster;
import android.renderscript.ProgramStore;
import android.renderscript.Sampler;
import android.renderscript.ProgramStore.BlendDstFunc;
import android.renderscript.ProgramStore.BlendSrcFunc;
import android.renderscript.ProgramVertex;
import android.renderscript.ProgramVertexFixedFunction;
import android.renderscript.RenderScriptGL;
import android.renderscript.ProgramVertexFixedFunction.Builder;
import android.util.Log;
import android.renderscript.Program;
import static android.renderscript.Sampler.Value.*;

public class GalaxyRS {
    public static final int BG_STAR_COUNT = 11000;
    public static final int SPACE_CLOUDSTAR_COUNT = 25;
    private Resources mRes;
    // rendering context
    private RenderScriptGL mRS;
    private ScriptC_galaxy mScript;

    // shader constants
    private ScriptField_VpConsts mPvConsts;
    private ScriptField_Particle spaceClouds;
    private ScriptField_Particle bgStars;
    private Mesh spaceCloudsMesh;
    private Mesh bgStarsMesh;

    int mHeight;
    int mWidth;
    boolean inited = false;

    private final BitmapFactory.Options mOptionsARGB = new BitmapFactory.Options();

    private Allocation cloudAllocation;
    private Allocation fgStarAllocation;
    private Allocation bgAllocation;

    public void init(RenderScriptGL rs, Resources res, int width, int height) {
        if (!inited) {
            mRS = rs;
            mRes = res;

            mWidth = width;
            mHeight = height;

            mOptionsARGB.inScaled = false;
            mOptionsARGB.inPreferredConfig = Bitmap.Config.ARGB_8888;

            spaceClouds = new ScriptField_Particle(mRS, SPACE_CLOUDSTAR_COUNT);
            Mesh.AllocationBuilder smb = new Mesh.AllocationBuilder(mRS);
            smb.addVertexAllocation(spaceClouds.getAllocation());
            smb.addIndexSetType(Mesh.Primitive.POINT);
            spaceCloudsMesh = smb.create();

            bgStars = new ScriptField_Particle(mRS, BG_STAR_COUNT);
            Mesh.AllocationBuilder smb2 = new Mesh.AllocationBuilder(mRS);
            smb2.addVertexAllocation(bgStars.getAllocation());
            smb2.addIndexSetType(Mesh.Primitive.POINT);
            bgStarsMesh = smb2.create();

            mScript = new ScriptC_galaxy(mRS, mRes, R.raw.galaxy);
            mScript.set_spaceCloudsMesh(spaceCloudsMesh);
            mScript.bind_spaceClouds(spaceClouds);
            mScript.set_bgStarsMesh(bgStarsMesh);
            mScript.bind_bgStars(bgStars);

            mPvConsts = new ScriptField_VpConsts(mRS, 1);

            createProgramVertex();
            createProgramRaster();
            createProgramFragmentStore();
            createProgramFragment();

            loadTextures();

            mRS.bindRootScript(mScript);

            mScript.invoke_positionParticles();

            inited = true;
        }

    }

    private Allocation loadTexture(int id) {
        final Allocation allocation = Allocation.createFromBitmapResource(mRS, mRes, id);
        return allocation;
    }

    private Allocation loadTextureARGB(int id) {
        Bitmap b = BitmapFactory.decodeResource(mRes, id, mOptionsARGB);
        return Allocation.createFromBitmap(mRS, b,
                Allocation.MipmapControl.MIPMAP_ON_SYNC_TO_TEXTURE,
                Allocation.USAGE_GRAPHICS_TEXTURE);
    }

    private void loadTextures() {
        fgStarAllocation = loadTexture(R.drawable.fgstar);
        cloudAllocation = loadTexture(R.drawable.cloud);
        bgAllocation = loadTexture(R.drawable.bg);
        mScript.set_textureSpaceCloud(cloudAllocation);
        mScript.set_textureFGStar(fgStarAllocation);
        mScript.set_textureBg(bgAllocation);
    }

    private Matrix4f getProjectionNormalized(int w, int h) {
        // range -1,1 in the narrow axis at z = 0.
        Matrix4f m1 = new Matrix4f();
        Matrix4f m2 = new Matrix4f();

        if (w > h) {
            float aspect = ((float) w) / h;
            m1.loadFrustum(-aspect, aspect, -1, 1, 1, 100);
        } else {
            float aspect = ((float) h) / w;
            m1.loadFrustum(-1, 1, -aspect, aspect, 1, 100);
        }

        m2.loadRotate(180, 0, 1, 0);
        m1.loadMultiply(m1, m2);

        m2.loadScale(-1, 1, 1);
        m1.loadMultiply(m1, m2);

        m2.loadTranslate(0, 0, 1);
        m1.loadMultiply(m1, m2);
        return m1;
    }

    private void updateProjectionMatrices() {
        Matrix4f proj = new Matrix4f();
        proj.loadOrthoWindow(mWidth, mHeight);

        Log.d("------------------- UPDATE PROJECTION MATRICES", mWidth + "  " + mHeight);

        Matrix4f projNorm = getProjectionNormalized(mWidth, mHeight);
        ScriptField_VpConsts.Item i = new ScriptField_VpConsts.Item();
        // i.Proj = projNorm;
        i.MVP = projNorm;
        mPvConsts.set(i, 0, true);

    }

    private void createProgramVertex() {

        // /////////////////// fixed function bg
        ProgramVertexFixedFunction.Constants mPvOrthoAlloc = 
            new ProgramVertexFixedFunction.Constants(mRS);
        Matrix4f proj = new Matrix4f();
        proj.loadOrthoWindow(mWidth, mHeight);
        mPvOrthoAlloc.setProjection(proj);

        ProgramVertexFixedFunction.Builder pvb = new ProgramVertexFixedFunction.Builder(mRS);
        ProgramVertex pv = pvb.create();
        ((ProgramVertexFixedFunction) pv).bindConstants(mPvOrthoAlloc);
        mScript.set_vertBg(pv);

        // ///////////////////////////////////////////////////////////////////////
        // //////////////////////////////////////////////////////////////////

        updateProjectionMatrices();

        // cloud
        ProgramVertex.Builder builder = new ProgramVertex.Builder(mRS);
        builder.setShader(mRes, R.raw.spacecloud_vs);
        builder.addConstant(mPvConsts.getType());
        builder.addInput(spaceCloudsMesh.getVertexAllocation(0).getType().getElement());
        ProgramVertex pvs = builder.create();
        pvs.bindConstants(mPvConsts.getAllocation(), 0);
        mRS.bindProgramVertex(pvs);

        mScript.set_vertSpaceClouds(pvs);

        // bg stars
        builder = new ProgramVertex.Builder(mRS);
        builder.setShader(mRes, R.raw.bgstar_vs);
        builder.addConstant(mPvConsts.getType());
        builder.addInput(bgStarsMesh.getVertexAllocation(0).getType().getElement());
        pvs = builder.create();
        pvs.bindConstants(mPvConsts.getAllocation(), 0);
        mRS.bindProgramVertex(pvs);
        mScript.set_vertBgStars(pvs);
    }

    private void createProgramFragment() {
        // fixed function bg

        Sampler.Builder samplerBuilder = new Sampler.Builder(mRS);
        samplerBuilder.setMinification(NEAREST);
        samplerBuilder.setMagnification(NEAREST);
        samplerBuilder.setWrapS(WRAP);
        samplerBuilder.setWrapT(WRAP);
        Sampler sn = samplerBuilder.create();
        ProgramFragmentFixedFunction.Builder builderff = 
            new ProgramFragmentFixedFunction.Builder(mRS);
        builderff = new ProgramFragmentFixedFunction.Builder(mRS);
        builderff.setTexture(ProgramFragmentFixedFunction.Builder.EnvMode.REPLACE,
                ProgramFragmentFixedFunction.Builder.Format.RGB, 0);
        ProgramFragment pfff = builderff.create();
        mScript.set_fragBg(pfff);
        pfff.bindSampler(sn, 0);

        ////////////////////////////////////////////////////////////////////

        // cloud fragment
        ProgramFragment.Builder builder = new ProgramFragment.Builder(mRS);

        builder.setShader(mRes, R.raw.spacecloud_fs);
        // multiple textures
        builder.addTexture(Program.TextureType.TEXTURE_2D);
        builder.addTexture(Program.TextureType.TEXTURE_2D);

        ProgramFragment pf = builder.create();
        pf.bindSampler(Sampler.CLAMP_LINEAR(mRS), 0);
        mScript.set_fragSpaceClouds(pf);

        // bg star fragment
        builder = new ProgramFragment.Builder(mRS);
        builder.setShader(mRes, R.raw.bgstar_fs);
        pf = builder.create();
        mScript.set_fragBgStars(pf);

    }

    private void createProgramRaster() {
        // Program raster is primarily used to specify whether point sprites are enabled and
        // to control the culling mode. By default, back faces are culled.
        ProgramRaster.Builder builder = new ProgramRaster.Builder(mRS);
        builder.setPointSpriteEnabled(true);
        ProgramRaster pr = builder.create();
        mRS.bindProgramRaster(pr);
    }

    private void createProgramFragmentStore() {
        // ProgramStore contains a set of parameters that control how the graphics hardware handles
        // writes to the framebuffer.
        // 
        // It could be used to:
        //     enable/disable depth testing
        //     specify wheather depth writes are performed
        //     setup various blending modes for use in effects like transparency
        //     define write masks for color components written into the framebuffer

        ProgramStore.Builder builder = new ProgramStore.Builder(mRS);
        // builder.setBlendFunc(BlendSrcFunc.SRC_ALPHA,
        // BlendDstFunc.ONE_MINUS_SRC_ALPHA );
        builder.setBlendFunc(BlendSrcFunc.SRC_ALPHA, BlendDstFunc.ONE);
        // why alpha no work with additive blending?
        // builder.setBlendFunc(BlendSrcFunc.ONE, BlendDstFunc.ONE);
        mRS.bindProgramStore(builder.create());

    }

    public void start() {
        mRS.bindRootScript(mScript);
    }

    public void stop() {
        mRS.bindRootScript(null);
    }

    public void setOffset(float xOffset, float yOffset, int xPixels, int yPixels) {
        mScript.set_xOffset(xOffset);
    }

}
