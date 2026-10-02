package dev.magnitude.client;

import dev.magnitude.physics.BodyPose;

public interface RenderPoseAccess {
    BodyPose magnitude$pose();
    void magnitude$pose(BodyPose pose);
}
