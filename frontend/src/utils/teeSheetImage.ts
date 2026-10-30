import { formatStartCell, publicStartColumn } from './teeSheetLayout';

export interface TeeSheetImageGroup {
  holeNumber: number | null;
  teeTime: string | null;
  names: (string | null)[];
}

export interface TeeSheetImageInput {
  tournamentName: string;
  courseName: string;
  fechaLabel: string;
  groups: TeeSheetImageGroup[];
}

const FONT = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';

const font = (weight: number, size: number) => `${weight} ${size}px ${FONT}`;

const roundRect = (
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
) => {
  const r = Math.min(radius, width / 2, height / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + width, y, x + width, y + height, r);
  ctx.arcTo(x + width, y + height, x, y + height, r);
  ctx.arcTo(x, y + height, x, y, r);
  ctx.arcTo(x, y, x + width, y, r);
  ctx.closePath();
};

const fitText = (ctx: CanvasRenderingContext2D, text: string, maxWidth: number) => {
  if (ctx.measureText(text).width <= maxWidth) return text;
  let value = text;
  while (value.length > 1 && ctx.measureText(`${value}…`).width > maxWidth) {
    value = value.slice(0, -1);
  }
  return `${value}…`;
};

/** Dibuja la grilla con el mismo criterio visual que la vista pública y devuelve un PNG. */
export const renderTeeSheetImage = async (input: TeeSheetImageInput): Promise<Blob> => {
  const scale = 2;
  const padding = 28;
  const titleSize = 22;
  const subtitleSize = 14;
  const headerSize = 12;
  const cellSize = 15;
  const cellPadX = 16;
  const headerHeight = 42;
  const rowHeight = 46;
  const gapAfterTitle = 8;
  const gapBeforeTable = 18;

  const startTitle = publicStartColumn(input.groups);
  const headers = [
    ...(startTitle ? [startTitle] : []),
    'Jugador 1',
    'Jugador 2',
    'Jugador 3',
    'Jugador 4',
  ];
  const rows = input.groups.map((group) => {
    const cells: string[] = [];
    if (startTitle) cells.push(formatStartCell(group.holeNumber, group.teeTime));
    for (let slot = 0; slot < 4; slot += 1) cells.push(group.names[slot] ?? '');
    return cells;
  });

  const measure = document.createElement('canvas').getContext('2d');
  if (!measure) throw new Error('No se pudo crear la imagen');

  const colWidths = headers.map((header, column) => {
    measure.font = font(700, headerSize);
    let width = measure.measureText(header.toUpperCase()).width;
    measure.font = font(600, cellSize);
    rows.forEach((row) => {
      width = Math.max(width, measure.measureText(row[column] ?? '').width);
    });
    const min = startTitle && column === 0 ? 128 : 160;
    return Math.min(340, Math.max(min, Math.ceil(width) + cellPadX * 2));
  });

  const playerStart = startTitle ? 1 : 0;
  const tableWidth = colWidths.reduce((sum, width) => sum + width, 0);
  const targetWidth = Math.max(tableWidth, 760);
  if (tableWidth < targetWidth) {
    const share = (targetWidth - tableWidth) / (headers.length - playerStart);
    for (let column = playerStart; column < colWidths.length; column += 1) {
      colWidths[column] += share;
    }
  }
  const roundedWidths = colWidths.map((width) => Math.round(width));
  const finalTableWidth = roundedWidths.reduce((sum, width) => sum + width, 0);
  const width = finalTableWidth + padding * 2;
  const tableTop = padding + titleSize + gapAfterTitle + subtitleSize + gapBeforeTable;
  const tableHeight = headerHeight + rows.length * rowHeight;
  const height = tableTop + tableHeight + padding;

  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = height * scale;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('No se pudo crear la imagen');
  ctx.scale(scale, scale);
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, width, height);

  ctx.textBaseline = 'top';
  ctx.fillStyle = '#0f172a';
  ctx.font = font(700, titleSize);
  ctx.fillText(fitText(ctx, input.tournamentName, width - padding * 2), padding, padding);

  ctx.fillStyle = '#475569';
  ctx.font = font(600, subtitleSize);
  const subtitle = `Campo: ${input.courseName}    Fecha: ${input.fechaLabel}`;
  ctx.fillText(fitText(ctx, subtitle, width - padding * 2), padding, padding + titleSize + gapAfterTitle);

  ctx.save();
  roundRect(ctx, padding, tableTop, finalTableWidth, tableHeight, 12);
  ctx.clip();
  ctx.fillStyle = '#1a3c2e';
  ctx.fillRect(padding, tableTop, finalTableWidth, headerHeight);

  rows.forEach((_, index) => {
    const y = tableTop + headerHeight + index * rowHeight;
    ctx.fillStyle = index % 2 === 1 ? '#f8fafc' : '#ffffff';
    ctx.fillRect(padding, y, finalTableWidth, rowHeight);
    ctx.strokeStyle = '#e2e8f0';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padding, y + 0.5);
    ctx.lineTo(padding + finalTableWidth, y + 0.5);
    ctx.stroke();
  });
  ctx.restore();

  ctx.strokeStyle = '#e2e8f0';
  ctx.lineWidth = 1;
  roundRect(ctx, padding + 0.5, tableTop + 0.5, finalTableWidth - 1, tableHeight - 1, 12);
  ctx.stroke();

  ctx.textBaseline = 'middle';
  ctx.font = font(700, headerSize);
  ctx.fillStyle = '#ffffff';
  let x = padding;
  headers.forEach((header, column) => {
    ctx.fillText(header.toUpperCase(), x + cellPadX, tableTop + headerHeight / 2);
    x += roundedWidths[column];
  });

  ctx.font = font(600, cellSize);
  rows.forEach((row, index) => {
    let cellX = padding;
    const y = tableTop + headerHeight + index * rowHeight + rowHeight / 2;
    row.forEach((value, column) => {
      const isStart = Boolean(startTitle) && column === 0;
      ctx.fillStyle = isStart ? '#334155' : '#1e293b';
      const maxWidth = roundedWidths[column] - cellPadX * 2;
      ctx.fillText(fitText(ctx, value, maxWidth), cellX + cellPadX, y);
      cellX += roundedWidths[column];
    });
  });

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (!blob) throw new Error('No se pudo crear la imagen');
  return blob;
};
