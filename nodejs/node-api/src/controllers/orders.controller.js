import * as svc from "../services/orders.service.js";

export async function buy(req, res) {
  const { itemId, quantity, idempotencyKey } = req.buy;
  const ip = (req.ip || req.socket?.remoteAddress || "").replace(/^::ffff:/, "");
  const order = await svc.createOrder({ itemId, quantity, idempotencyKey, buyerIp: ip });
  res.status(201).json({
    id: order.id,
    itemId: order.itemId,
    quantity: order.quantity,
    unitPrice: order.unitPrice,
    totalPrice: order.totalPrice,
    status: order.status,
    createdAt: order.createdAt
  });
}

import { redeemCouponVuln, redeemCouponSafe } from "../services/orders.service.js";

export async function redeemVuln(req, res) {
  const { userId, code } = req.couponReq;
  const r = await redeemCouponVuln({ userId, code });
  res.status(201).json({ id: r.id, userId, couponId: r.couponId, createdAt: r.createdAt, mode: "vuln" });
}

export async function redeemSafe(req, res) {
  const { userId, code } = req.couponReq;
  const r = await redeemCouponSafe({ userId, code });
  res.status(201).json({ id: r.id, userId, couponId: r.couponId, createdAt: r.createdAt, mode: "safe" });
}

