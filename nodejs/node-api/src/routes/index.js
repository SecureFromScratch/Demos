import { Router } from "express";
import items from "./items.routes.js";
import users from "./users.routes.js";

const router = Router();
router.use("/items", items);
router.use("/users", users);

export default router;
