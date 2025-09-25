import { Router } from "express";
import asyncHandler from "../middlewares/asyncHandler.js";
import { validateBuy } from "../middlewares/validateBuy.js";
import { buy } from "../controllers/orders.controller.js";
import { validateCoupon } from "../middlewares/validateCoupon.js";
import { redeemVuln, redeemSafe } from "../controllers/orders.controller.js";




const r = Router();
r.post("/buy", validateBuy, asyncHandler(buy));
r.post("/redeem-coupon-vuln", validateCoupon, asyncHandler(redeemVuln));
r.post("/redeem-coupon-safe", validateCoupon, asyncHandler(redeemSafe));

export default r;
