#pragma version(1)

#pragma rs java_package_name(com.android.galaxy4)
#include "rs_graphics.rsh"
#pragma stateVertex(parent);
#pragma stateStore(parent);

typedef struct __attribute__((packed, aligned(4))) Particle {
    float3 position;
    uchar4 color;
} Particle_t;

typedef struct VpConsts {
    rs_matrix4x4 MVP;
} VpConsts_t;
VpConsts_t *vpConstants;


// hold clouds
Particle_t *spaceClouds;

// hold bg stars
Particle_t *bgStars;

rs_mesh spaceCloudsMesh;
rs_mesh bgStarsMesh;

rs_program_vertex vertSpaceClouds;
rs_program_vertex vertBgStars;
rs_program_fragment fragSpaceClouds;
rs_program_fragment fragBgStars;
rs_program_vertex vertBg;
rs_program_fragment fragBg;

rs_allocation textureSpaceCloud;
rs_allocation textureFGStar;
rs_allocation textureBg;

static int gGalaxyRadius = 250;

float xOffset;

#define PI 3.1415f
#define TWO_PI 6.283f

/**
 * Helper function to generate the stars.
 */
static float randomGauss() {
    float x1;
    float x2;
    float w = 2.f;

    while (w >= 1.0f) {
        x1 = rsRand(2.0f) - 1.0f;
        x2 = rsRand(2.0f) - 1.0f;
        w = x1 * x1 + x2 * x2;
    }

    w = sqrt(-2.0f * log(w) / w);
    return x1 * w;
}

static float mapf(float minStart, float minStop, float maxStart, float maxStop, float value) {
    return maxStart + (maxStart - maxStop) * ((value - minStart) / (minStop - minStart));
}



void positionParticles(){
    rsDebug("************************&&&&&&&&&&&&&&& Called positionBGStars", rsUptimeMillis());

    float width = rsgGetWidth();
    float height = rsgGetHeight();
    
    float scale = gGalaxyRadius / (width * 0.5f);
    
    // space clouds 
    Particle_t* particle = spaceClouds;
    int size = rsAllocationGetDimX(rsGetAllocation(spaceClouds));
    for(int i=0; i<size; i++){
    
        float d = fabs(randomGauss()) * gGalaxyRadius * 0.5f + rsRand(64.0f);
    
        d = mapf(-4.0f, gGalaxyRadius + 4.0f, 0.0f, scale, d);
        
        float id = d / gGalaxyRadius;
        float z = randomGauss() * 0.4f * (1.0f - id);
        
        if (d > gGalaxyRadius * 0.15f) {
            z *= 0.6f * (1.0f - id);
        } else {
            z *= 0.72f;
        }
        
        particle->position.x = rsRand(TWO_PI);
        particle->position.y = d;
        particle->position.z = z/5.0f;
        particle->color = rsPackColorTo8888(1.0f, 0.0f, 1.0f);
        particle++;
    }
    
    // bg stars
    size = rsAllocationGetDimX(rsGetAllocation(bgStars));
    particle = bgStars;
    for(int i=0; i<size; i++){
        float d = fabs(randomGauss()) * gGalaxyRadius * 0.5f + rsRand(64.0f);
    
        d = mapf(-4.0f, gGalaxyRadius + 4.0f, 0.0f, scale, d);
        
        float id = d / gGalaxyRadius;
        float z = randomGauss() * 0.4f * (1.0f - id);
        
        if (d > gGalaxyRadius * 0.15f) {
            z *= 0.6f * (1.0f - id);
        } else {
            z *= 0.72f;
        }
        
        particle->position.x = rsRand(TWO_PI);
        particle->position.y = d;
        particle->position.z = z/5.0f;
        particle->color = rsPackColorTo8888(1.0f, 0.0f, 1.0f);
        particle++;
    }
    
    
}

static void drawBg(int width, int height){
    rsgBindTexture(fragBg, 0, textureBg);
    rsgDrawRect(0.0f, 0.0f, width, height, 0.0f);
}

int root(){
    float width = rsgGetWidth();
    float height = rsgGetHeight();
    
    
    rsgClearColor(0.0f, 0.f, 0.f, 0.5f);
    
    // bg
    rsgBindProgramVertex(vertBg);
    rsgBindProgramFragment(fragBg);
    drawBg(width, height);
    
    
    // space cloud
    rsgBindProgramVertex(vertSpaceClouds);
    int size = rsAllocationGetDimX(rsGetAllocation(spaceClouds));
    Particle_t *particle = spaceClouds;
    
    for(int i=0; i<size; i++){
        particle->position.x -= .065;
        particle++;
    }
    rsgBindProgramFragment(fragSpaceClouds);
    rsgBindTexture(fragSpaceClouds, 0, textureSpaceCloud);
    rsgBindTexture(fragSpaceClouds, 1, textureFGStar);
    rsgDrawMesh(spaceCloudsMesh);
    
    
    
    // bg stars
    rsgBindProgramVertex(vertBgStars);
    size = rsAllocationGetDimX(rsGetAllocation(bgStars));
    particle = bgStars;
    
    for(int i=0; i<size; i++){
        particle->position.x -= .007;
        particle++;
    }
    rsgBindProgramFragment(fragBgStars);
    rsgDrawMesh(bgStarsMesh);


    return 40;
}

