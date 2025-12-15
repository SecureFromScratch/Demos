import * as svc from "../services/items.service.js";
import { normalizeQuery } from "../utils/normalize.js";
//import { normalizeQuery } from "../utils/normalize.js";



export async function getByCriteria(req, res) {
  const criteria = req.criteria ?? {};
  if (Object.keys(criteria).length === 0) {
    return res.status(400).json({ error: "At least one of category|price|active is required" });
  }
  const items = await svc.getItemsByCriteria(criteria);

  res.json(items);
}

export async function getByCriteriaVuln(req, res) {
  const items = await svc.getItemsByCriteriaVuln(req.query ?? {});
  res.json(items);
}

export async function list(req, res) {
  const items = await svc.listItems();

  const enriched = await Promise.all(
    items.map(async (it) => {
      if (!it.imageUrl) return it;
      try {
        const resp = await fetch(it.imageUrl, { redirect: "follow" });
        if (!resp.ok) throw new Error();
        const buf = Buffer.from(await resp.arrayBuffer());
        return { ...it, imageBase64: buf.toString("base64") };
      } catch {
        return { ...it, imageBase64: null };
      }
    })
  );

  res.json(enriched);
}



/*export async function create(req, res) {
  const item = await svc.createItem(req.body);
  res.status(201).json(item);

  //const item = await svc.createItemWithFile(req.itemData, req.file ?? null);
  //return res.status(201).json(item);

}
*/

// JSON only (no file)
/*export async function create(req, res) {
  if (!req.itemData) return res.status(400).json({ error: "Invalid body" });
  const item = await svc.create(req.itemData);
  res.status(201).json(item);
}
*/
export async function create(req, res) {
  if (!req.itemData) return res.status(400).json({ error: "Invalid body" });
  const item = await svc.create(req.itemData);
  res.status(201).json(item);
}



// multipart + file
export async function createWithFile(req, res) {
  const item = await svc.createItemWithFile(req.itemData, req.file ?? null);
  return res.status(201).json(item);
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

