package learn.gjx.tuku1.manager.space;

/** Permission names are shared with the frontend; membership is always resolved server-side. */
public interface SpacePermission {
    String VIEW = "picture:view";
    String UPLOAD = "picture:upload";
    String EDIT = "picture:edit";
    String DELETE = "picture:delete";
    String MEMBERS = "spaceUser:manage";
    String ANALYZE = "space:analyze";
}
